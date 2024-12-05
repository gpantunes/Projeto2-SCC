package tukano.impl;

import static java.lang.String.format;
import static tukano.api.Result.ok;
import static tukano.api.Result.error;
import static tukano.api.Result.errorOrResult;
import static tukano.api.Result.errorOrValue;
import static tukano.api.Result.ErrorCode.BAD_REQUEST;
import static tukano.api.Result.ErrorCode.FORBIDDEN;

//import static tukano.auth.Authentication.login;

//import java.util.Collection;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import com.azure.cosmos.CosmosException;
import com.fasterxml.jackson.databind.ObjectMapper;
import redis.clients.jedis.Jedis;
import tukano.api.Result;
import tukano.api.Result.ErrorCode;
//import tukano.auth.Authentication;
import tukano.impl.rest.TukanoRestServer;
import tukano.api.User;
import tukano.api.Users;
import utils.DB;
import utils.RedisCache;
import tukano.clients.BlobsClient;
import tukano.clients.RedisClient;

public class JavaUsers implements Users {

	private static Logger Log = Logger.getLogger(JavaUsers.class.getName());

	private static Users instance;
	private BlobsClient blobsClient = new BlobsClient("http://blobs-logic-service.default.svc.cluster.local:8080");
	private RedisClient redisClient = new RedisClient("http://redis-service.default.svc.cluster.local:8080");

	// flags para definir o que se vai utilizar
	private static final boolean cacheOn = TukanoRestServer.cacheOn;

	//private static final Authentication auth = new Authentication();

	synchronized public static Users getInstance() {
		if (instance == null)
			instance = new JavaUsers();
		return instance;
	}

	private JavaUsers() {
	}

	@Override
	public Result<String> createUser(User user) {
		Log.info(() -> format("createUser : %s\n", user));

		if (badUserInfo(user))
			return error(BAD_REQUEST);

		Result<String> res;

		res = errorOrValue(DB.insertOne(user), user.getUserId());

		if (cacheOn)
			this.putInCache(user.userId(), user.toString());

		return res;
	}

	@Override
	public Result<User> getUser(String userId, String pwd) {
		Log.info(() -> format("getUser : userId = %s, pwd = %s\n", userId, pwd));

		if (userId == null)
			return error(BAD_REQUEST);

		if (cacheOn) {

			try (Jedis jedis = RedisCache.getCachePool().getResource()) {
				String dataOnCache = jedis.get(userId);

				if (dataOnCache != null) {
					Log.info("%%%%%%%%%%%%%%%%%%% conseguiu obter da cache");
					Result<User> item = validatedUserOrError(parseUserFromString(dataOnCache),
							pwd);
					return item;
				} else {
					Result<User> userRes;

					userRes = validatedUserOrError(DB.getOne(userId, User.class), pwd);

					if (userRes.isOK()) {
						User item = userRes.value();
						Log.info("%%%%%%%%%%%%%%%%%%% foi buscar ao cosmos " + item);
						jedis.set(userId, item.toString());
						Log.info("&&&&&&&&&&&&&&&&&& meteu no jedis");
					}

					//userRes.setCookie(auth.login(userId, pwd)); // é preciso criar um objeto que junte o user e a cookie
																// num tuplo para retornar Result<tuplo>
					return userRes;
				}

			} catch (CosmosException e) {
				return Result.error(errorCodeFromStatus(e.getStatusCode()));
			} catch (Exception e) {
				e.printStackTrace();
				return Result.error(ErrorCode.INTERNAL_ERROR);
			}
		}

		else {
			return validatedUserOrError(DB.getOne(userId, User.class), pwd);
		}
	}

	@Override
	public Result<User> updateUser(String userId, String pwd, User other) {
		Log.info(() -> format("updateUser : userId = %s, pwd = %s, user: %s\n", userId, pwd, other));

		if (badUpdateUserInfo(userId, pwd, other))
			return error(BAD_REQUEST);

		Result<User> oldUser = getUser(userId, pwd);

		User u1 = oldUser.value();

		User newUser = u1.updateFrom(other);

		Result<User> userDB;

		userDB = DB.updateOne(newUser);

		if (cacheOn)
			this.putInCache(userId, newUser.toString());

		return errorOrResult(oldUser, user -> userDB);
	}

	@Override
	public Result<User> deleteUser(String userId, String pwd) {
		Log.info(() -> format("deleteUser : userId = %s, pwd = %s\n", userId, pwd));

		if (userId == null || pwd == null)
			return error(BAD_REQUEST);

		Result<User> userDB = this.getUser(userId, pwd);

		return errorOrResult(validatedUserOrError(userDB, pwd),
				user -> {
					// Delete user shorts and related info asynchronously in a separate thread
					Executors.defaultThreadFactory().newThread(() -> {
						//JavaBlobs.getInstance().deleteAllBlobs(userId, Token.get(userId));
						blobsClient.deleteAllBlobs(userId, Token.get(userId));
						JavaShorts.getInstance().deleteAllShorts(userId, pwd, Token.get(userId));

						DB.deleteOne(user);

					}).start();

					if (cacheOn)
						this.delInCache(userId);

					return userDB;
				});
	}

	@Override
	public Result<List<User>> searchUsers(String pattern) {
		Log.info(() -> format("searchUsers : patterns = %s\n", pattern));

		var query = format("SELECT * FROM users u WHERE UPPER(u.id) LIKE '%%%s%%'", pattern.toUpperCase());

		if (cacheOn) {
			try (Jedis jedis = RedisCache.getCachePool().getResource()) {

				String cacheKey = String.valueOf(query.hashCode());
				byte[] dataOnCache = jedis.get(cacheKey.getBytes());
				Result<List<User>> data;

				if (dataOnCache == null) {

					query = String.format("SELECT * FROM \"user\" u WHERE UPPER(u.userId) LIKE '%%%s%%'",
							pattern.toUpperCase());
					data = ok(DB.sql(query, User.class)
							.stream()
							.map(User::copyWithoutPassword)
							.toList());

					Log.info("Foi buscar os users à CosmosDB");
					if (data.isOK())
						jedis.setex(cacheKey.getBytes(), 20, serialize(data.value()));
				} else
					data = Result.ok(deserializeList(dataOnCache));

				return data;

			} catch (CosmosException e) {
				return Result.error(errorCodeFromStatus(e.getStatusCode()));
			} catch (Exception e) {
				e.printStackTrace();
				return Result.error(ErrorCode.INTERNAL_ERROR);
			}
		}

		else {

			query = String.format("SELECT * FROM \"user\" u WHERE UPPER(u.userId) LIKE '%%%s%%'",
					pattern.toUpperCase());
			return ok(DB.sql(query, User.class)
					.stream()
					.map(User::copyWithoutPassword)
					.toList());

		}

	}

	private <T> byte[] serialize(T obj) {
		try {
			ObjectMapper objectMapper = new ObjectMapper();
			return objectMapper.writeValueAsBytes(obj); // Serializa o objeto como JSON em byte[]
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	private <T> List<T> deserializeList(byte[] data) {
		try {
			ObjectMapper objectMapper = new ObjectMapper();
			// Convertemos o byte[] em uma lista de objetos do tipo especificado
			return objectMapper.readValue(data,
					objectMapper.getTypeFactory().constructCollectionType(List.class, User.class));
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	private Result<User> parseUserFromString(String userString) {
		try {
			// Extrai os valores com base na estrutura conhecida da string
			String userId = userString.split("userId=")[1].split(",")[0].trim();
			String pwd = userString.split("pwd=")[1].split(",")[0].trim();
			String email = userString.split("email=")[1].split(",")[0].trim();
			String displayName = userString.split("displayName=")[1].split("]")[0].trim();

			User user = new User(userId, pwd, email, displayName);
			return Result.ok(user);
		} catch (Exception e) {
			Log.warning("Erro ao transformar o User da cache, que vem como String, para User");
			return Result.error(ErrorCode.INTERNAL_ERROR);
		}
	}

	private Result<User> validatedUserOrError(Result<User> res, String pwd) {
		if (res.isOK())
			return res.value().getPwd().equals(pwd) ? res : error(FORBIDDEN);
		else
			return res;
	}

	private boolean badUserInfo(User user) {
		return (user.userId() == null || user.pwd() == null || user.displayName() == null || user.email() == null);
	}

	private boolean badUpdateUserInfo(String userId, String pwd, User info) {
		return (userId == null || pwd == null || info.getUserId() != null && !userId.equals(info.getUserId()));
	}

	private Result<Void> putInCache(String id, String obj) {
		try (Jedis jedis = RedisCache.getCachePool().getResource()) {

			jedis.set(id, obj);
			Log.info("Adicionou objeto à cache");
			return ok();

		} catch (Exception e) {
			e.printStackTrace();
			return Result.error(ErrorCode.INTERNAL_ERROR);
		}
	}

	private Result<Void> delInCache(String id) {

		try (Jedis jedis = RedisCache.getCachePool().getResource()) {

			jedis.del(id);
			Log.info("Apagou objeto da cache");
			return ok();

		} catch (Exception e) {
			e.printStackTrace();
			return Result.error(ErrorCode.INTERNAL_ERROR);
		}
	}

	static Result.ErrorCode errorCodeFromStatus(int status) {
		return switch (status) {
			case 200 ->
				ErrorCode.OK;
			case 404 ->
				ErrorCode.NOT_FOUND;
			case 409 ->
				ErrorCode.CONFLICT;
			default ->
				ErrorCode.INTERNAL_ERROR;
		};
	}
}
