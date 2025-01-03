package tukano.impl;

import static java.lang.String.format;
import static tukano.api.Result.ErrorCode.*;
import static tukano.api.Result.ok;
import static tukano.api.Result.error;
import static tukano.api.Result.errorOrResult;
import static tukano.api.Result.errorOrValue;

//import java.util.Collection;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import com.azure.cosmos.CosmosException;
import com.fasterxml.jackson.databind.ObjectMapper;
import tukano.api.Result;
import tukano.api.Result.ErrorCode;
import tukano.auth.Authentication;
import tukano.impl.rest.TukanoRestServer;
import tukano.api.User;
import tukano.api.Users;
import utils.DB;
import tukano.clients.BlobsClient;
import tukano.auth.CookieStore;
import static tukano.auth.CookieStore.get;

import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;

public class JavaUsers implements Users {

	private static Logger Log = Logger.getLogger(JavaUsers.class.getName());

	private static Users instance;
	private static BlobsClient blobsClient = new BlobsClient("http://blobs-service:80/tukano-1.0/rest");

	private static Authentication auth = new Authentication();

	synchronized public static Users getInstance() {
		if (instance == null)
			instance = new JavaUsers();
		return instance;
	}

	private JavaUsers() {
	}

	@Override
	public Result<String> login(String userId, String pwd) {

		Result userRes = getUser(userId, pwd);
		if(!userRes.isOK()) {
			return Result.error(BAD_REQUEST);
		}

		Response authRes = auth.login(userId, pwd);
		String cookie = authRes.getHeaderString("Set-Cookie");

		System.out.println("################## cookie " + cookie);

		Result res;
		if(cookie != null) {
			try {
				res = blobsClient.setCookie(cookie, userId);
				if(!res.isOK()) {
					return Result.error(ErrorCode.INTERNAL_ERROR);
				}
			} catch(Exception e) {
				e.printStackTrace();
			}
		}

		CookieStore.getInstance().set(userId, cookie);
		return Result.ok(cookie);
	}

	@Override
	public Result<String> createUser(User user) {
		Log.info(() -> format("createUser : %s\n", user));

		if (badUserInfo(user))
			return error(BAD_REQUEST);

		Result<String> res;

		res = errorOrValue(DB.insertOne(user), user.getUserId());
		return res;
	}

	@Override
	public Result<User> getUser(String userId, String pwd) {
		Log.info(() -> format("getUser : userId = %s, pwd = %s\n", userId, pwd));

		if (userId == null)
			return error(BAD_REQUEST);

		try {
			Result<User> userRes;
			userRes = validatedUserOrError(DB.getOne(userId, User.class), pwd);

			if (userRes.isOK()) {
				User item = userRes.value();
				Log.info("%%%%%%%%%%%%%%%%%%% foi buscar à DB " + item);
			}

			return userRes;
		} catch (Exception e) {
			e.printStackTrace();
			return Result.error(ErrorCode.INTERNAL_ERROR);
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

		Result<User> res;

		return ok(newUser);

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
						try {
							blobsClient.deleteAllBlobs(userId, CookieStore.get(userId));
							JavaShorts.getInstance().deleteAllShorts(userId, pwd, CookieStore.get(userId));
							System.out.println("Vai tentar apagar um user");
							DB.deleteOne(userDB.value());
						} catch(Exception e){
							e.printStackTrace();
						}
					}).start();

					return userDB;
				});
	}

	@Override
	public Result<List<User>> searchUsers(String pattern) {
		Log.info(() -> format("searchUsers : patterns = %s\n", pattern));

		var query = format("SELECT * FROM users u WHERE UPPER(u.id) LIKE '%%%s%%'", pattern.toUpperCase());
			try {
				Result<List<User>> data;

					query = String.format("SELECT * FROM \"user\" u WHERE UPPER(u.userId) LIKE '%%%s%%'",
							pattern.toUpperCase());
					data = ok(DB.sql(query, User.class)
							.stream()
							.map(User::copyWithoutPassword)
							.toList());

					Log.info("Foi buscar os users à CosmosDB");

				return data;

			} catch (Exception e) {
				e.printStackTrace();
				return Result.error(ErrorCode.INTERNAL_ERROR);
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
