package tukano.impl;

import static java.lang.String.format;
import static tukano.api.Result.ErrorCode.*;
import static tukano.api.Result.error;
import static tukano.api.Result.errorOrResult;
import static tukano.api.Result.errorOrValue;
import static tukano.api.Result.errorOrVoid;
import static tukano.api.Result.ok;
//import static tukano.api.Result.ErrorCode.OK;
//import static utils.DB.getOne;
import tukano.clients.BlobsClient;
import tukano.clients.RedisClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import com.azure.cosmos.CosmosException;
import com.fasterxml.jackson.databind.ObjectMapper;

import redis.clients.jedis.Jedis;

import tukano.api.Blobs;
import tukano.api.Result;
import tukano.api.Result.ErrorCode;
import tukano.api.Short;
import tukano.api.Shorts;
import tukano.api.User;
import tukano.impl.data.Following;
import tukano.impl.data.Likes;
import tukano.impl.rest.TukanoRestServer;
import utils.DB;
import utils.RedisCache;

public class JavaShorts implements Shorts {

    private static Logger Log = Logger.getLogger(JavaShorts.class.getName());

    private static Shorts instance;
    private static BlobsClient blobsClient = new BlobsClient("http://blobs-logic-service.default.svc.cluster.local:8080");
    private static RedisClient redisClient = new RedisClient("http://redis-service.default.svc.cluster.local:8080");

    synchronized public static Shorts getInstance() {
        if (instance == null) {
            instance = new JavaShorts();
        }
        return instance;
    }

    private JavaShorts() {
    }

    @Override
    public Result<Short> createShort(String userId, String password) {
        Log.info(() -> format("createShort : userId = %s, pwd = %s\n", userId, password));

        return errorOrResult(okUser(userId, password), user -> {

            var shortId = format("%s+%s", userId, UUID.randomUUID());
            var blobUrl = format("%s/%s/%s", TukanoRestServer.serverURI, Blobs.NAME, shortId);

            Log.info("Tukano: " + TukanoRestServer.serverURI + " BlobName: " + Blobs.NAME);

            var shrt = new Short(shortId, userId, blobUrl);

            Result<Short> shortDb = DB.insertOne(shrt);

            this.putInCache(shrt.getShortId(), shrt.toString());

            return errorOrValue(shortDb,
                    s -> s.copyWithLikes_And_Token(0));
        });
    }

    @SuppressWarnings("unchecked")
    @Override
    public Result<Short> getShort(String shortId) {
        Log.info(() -> format("getShort : shortId = %s\n", shortId));

        if (shortId == null) {
            return error(BAD_REQUEST);
        }

        Result<Short> shortRes;
        Result<List<Long>> like;

        var query = format("SELECT COUNT(l.shortId) FROM \"likes\" l WHERE l.shortId = '%s'", shortId);

        like = (Result<List<Long>>) this.tryQuery(query, "likes",
                Long.class);

        shortRes = this.getFromCache(shortId, like);
        return shortRes;

    }

    @Override
    public Result<Void> deleteShort(String shortId, String password) {
        Log.info(() -> format("deleteShort : shortId = %s, pwd = %s\n", shortId, password));

        return errorOrResult(this.getShort(shortId), shrt -> {

            return errorOrResult(okUser(shrt.getOwnerId(), password), user -> {


                this.delInCache(shortId);

                // Delete associated blob
                //JavaBlobs.getInstance().delete(shrt.getShortId(), Token.get());
                try {
                    blobsClient.deleteBlob(shrt.getShortId(), Token.get());
                }catch (Exception e){
                    return Result.error(INTERNAL_ERROR);
                }

                return DB.transaction(hibernate -> {
                    hibernate.remove(shrt);
                    var query = format("DELETE FROM \"likes\" l WHERE l.shortId = '%s'", shortId);
                    hibernate.createNativeQuery(query, Likes.class).executeUpdate();
                });

            });
        });
    }

    @SuppressWarnings("unchecked")
    @Override
    public Result<List<String>> getShorts(String userId) {
        Log.info(() -> format("getShorts : userId = %s\n", userId));

        List<String> l = new ArrayList<>();

        var query = format("SELECT s.shortId FROM \"short\" s WHERE s.ownerId = '%s'", userId);

        Result<List<String>> data = (Result<List<String>>) this.tryQuery(query, "shorts",
                String.class);

        for (String str : data.value()) {
            Short s = this.getShort(str).value();
            l.add("ShortId: " + s.getShortId() + " TotalLikes: " + s.getTotalLikes());
        }

        return errorOrValue(okUser(userId), l);

    }

    @Override
    public Result<Void> follow(String userId1, String userId2, boolean isFollowing, String password) {
        Log.info(() -> format("follow : userId1 = %s, userId2 = %s, isFollowing = %s, pwd = %s\n", userId1, userId2,
                isFollowing, password));

        return errorOrResult(okUser(userId1, password), user -> {

            var f = new Following(userId1, userId2);
            Log.info("Cria o objeto follow");

            Result<Void> res = okUser(userId2);

            if (res.isOK()) {
                Result<Following> resDB;

                if (isFollowing) {
                    resDB = DB.insertOne(f);
                    this.putInCache(userId1 + ":" + userId2, f.toString());
                } else {
                    resDB = DB.deleteOne(f);
                    this.delInCache(userId1 + ":" + userId2);
                }

                return errorOrVoid(res, resDB);
            } else
                return res;
        });
    }

    @SuppressWarnings("unchecked")
    @Override
    public Result<List<String>> followers(String userId, String password) {
        Log.info(() -> format("followers : userId = %s, pwd = %s\n", userId, password));

        var query = format("SELECT f.follower FROM \"followers\" f WHERE f.followee = '%s'", userId);

        Result<List<String>> data = (Result<List<String>>) this.tryQuery(query, "followers",
                String.class);

        return errorOrValue(okUser(userId, password), data);

    }

    @Override
    public Result<Void> like(String shortId, String userId, boolean isLiked, String password) {
        Log.info(() -> format("like : shortId = %s, userId = %s, isLiked = %s, pwd = %s\n", shortId, userId, isLiked,
                password));

        return errorOrResult(getShort(shortId), shrt -> {
            var l = new Likes(userId, shortId, shrt.getOwnerId());
            Log.info("Objeto Like criado");

            Result<User> res = okUser(userId, password);

            if (res.isOK()) {

                Result<Likes> resDB;

                if (isLiked) {

                    resDB = DB.insertOne(l);

                    if (resDB.isOK())
                        this.putInCache(userId + "_" + shortId, l.toString());

                } else {

                    resDB = DB.deleteOne(l);

                    if (resDB.isOK())
                        this.delInCache(userId + "_" + shortId);

                }

                return errorOrVoid(res, resDB);
            } else
                return errorOrVoid(res, res);
        });
    }

    @SuppressWarnings("unchecked")
    @Override
    public Result<List<String>> likes(String shortId, String password) {
        Log.info(() -> format("likes : shortId = %s, pwd = %s\n", shortId, password));

        return errorOrResult(getShort(shortId), shrt -> {

           var query = format("SELECT l.userId FROM \"likes\" l WHERE l.shortId = '%s'", shortId);

            Result<List<String>> data = (Result<List<String>>) this.tryQuery(query, "likes",
                    String.class);

            return errorOrValue(okUser(shrt.getOwnerId(), password), data);

        });
    }

    @SuppressWarnings("unchecked")
    @Override
    public Result<List<String>> getFeed(String userId, String password) {
        Log.info(() -> format("getFeed : userId = %s, pwd = %s\n", userId, password));

        List<String> l = new ArrayList<>();

        var query1 = format("SELECT * FROM \"short\" s WHERE s.ownerId = '%s'", userId);

        Result<List<Short>> data = (Result<List<Short>>) this.tryQuery(query1, "shorts", Short.class);

        for (Short shrt : data.value())
            l.add("ShortId: " + shrt.getShortId() + " TimeStamp: " + shrt.getTimestamp());

        var query2 = format("SELECT f.followee FROM \"followers\" f WHERE  f.follower = '%s'", userId);

        Result<List<String>> data2 = (Result<List<String>>) this.tryQuery(query2, "followers",
                String.class);

        Log.warning("A entrar nos shorts dos meus seguidores");
        for (String s : data2.value()) {
            Log.warning(s);

            var query3 = format("SELECT * FROM \"short\" s WHERE s.ownerId = '%s' ORDER BY s.timestamp DESC",
                        s);

            Result<List<Short>> data3 = (Result<List<Short>>) this.tryQuery(query3, "shorts",
                    Short.class);

            for (Short shrt : data3.value())
                l.add("ShortId: " + shrt.getShortId() + " TimeStamp: " + shrt.getTimestamp());

        }

        return errorOrValue(okUser(userId, password), l);

    }

    @SuppressWarnings("unchecked")
    @Override
    public Result<Void> deleteAllShorts(String userId, String password, String token) {
        Log.info(() -> format("deleteAllShorts : userId = %s, password = %s, token = %s\n", userId, password, token));

        if (!Token.isValid(token, userId)) {
            return error(FORBIDDEN);
        }

        // delete shorts
        Log.warning("Está a tentar apagar os shorts");
        var query1 = format("SELECT * FROM \"short\" s WHERE s.ownerId = '%s'", userId);

        Result<List<Short>> data = (Result<List<Short>>) this.tryQuery(query1, "shorts",
                Short.class);

        for (Short s : data.value()) {
            DB.deleteOne(s);
            this.delInCache(s.getShortId());
            Log.warning("Apagou 1 short");
        }

        // delete follows
        Log.warning("Está a tentar apagar os follows");
        var query2 = format("SELECT * FROM \"followers\" f WHERE f.follower = '%s' OR f.followee = '%s'", userId,
                    userId);

        Result<List<Following>> data2 = (Result<List<Following>>) this.tryQuery(query2, "followers",
                Following.class);

        for (Following f : data2.value()) {
            DB.deleteOne(f);
            this.delInCache(f.getFollower() + ":" + f.getFollowee());
            Log.warning("Apagou 1 Follow");
        }

        // delete likes
        Log.warning("Está a tentar apagar os likes");

        var query3 = format("SELECT * FROM \"likes\" l WHERE l.ownerId = '%s' OR l.userId = '%s'", userId, userId);

        Result<List<Likes>> data3 = (Result<List<Likes>>) this.tryQuery(query3, "likes", Likes.class);

        for (Likes l : data3.value()) {
            DB.deleteOne(l);
            this.delInCache(l.getUserId() + "_" + l.getShortId());
            Log.warning("Apagou 1 Like");
        }

        return ok();

    }

    protected Result<User> okUser(String userId, String pwd) {
        return JavaUsers.getInstance().getUser(userId, pwd);
    }

    /*
     * this method was developed by chatGPT
     * 
     * It serializes an object/set of objects to put in cache
     */
    private <T> byte[] serialize(T obj) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.writeValueAsBytes(obj); // Serializa o objeto como JSON em byte[]
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /*
     *
     * this method was mostly develop by chatGPT
     * 
     * It desirerializes a set of data to a list of objects of the Class clazz
     */
    private <T> List<T> deserializeList(byte[] data, Class<T> clazz) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            // Convertemos o byte[] em uma lista de objetos do tipo especificado
            return objectMapper.readValue(data,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, clazz));
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /*
     * this method was partially develop by chatGPT
     * 
     * It transforms a String to a Short when we try to retrieve the object from
     * cache
     */
    private Result<Short> parseShortFromString(String shortString) {

        try {
            // Extract values by splitting the string based on known structure
            String shortId = shortString.split("shortId=")[1].split(",")[0].trim();
            String ownerId = shortString.split("ownerId=")[1].split(",")[0].trim();
            String blobUrl = shortString.split("blobUrl=")[1].split(",")[0].trim();
            Long timestamp = Long.parseLong(shortString.split("timestamp=")[1].split(",")[0].trim());
            int totalLikes = Integer.parseInt(shortString.split("totalLikes=")[1].split("]")[0].trim());

            Short shrt = new Short(shortId, ownerId, blobUrl, timestamp, totalLikes);
            return Result.ok(shrt);
        } catch (Exception e) {
            Log.warning("Erro ao transformar o User da cache, que vem como String, para User");
            return Result.error(ErrorCode.INTERNAL_ERROR);
        }
    }

    private Result<Void> okUser(String userId) {
        var res = okUser(userId, "");
        Log.info(res.error().toString());
        if (res.error() == FORBIDDEN) {
            return ok();
        } else {
            return error(res.error());
        }
    }

    /*
     * puts an object in cache
     */
    private Result<Void> putInCache(String id, String obj) {
        try {
            redisClient.set(id, obj);
            Log.info("Adicionou objeto à cache");
            return ok();

        } catch (Exception e) {
            e.printStackTrace();
            return Result.error(ErrorCode.INTERNAL_ERROR);
        }
    }

    /*
     * deletes an object in cache
     */
    private Result<Void> delInCache(String id) {

        try {
            redisClient.delete(id);
            Log.info("Apagou objeto da cache");
            return ok();

        } catch (Exception e) {
            e.printStackTrace();
            return Result.error(ErrorCode.INTERNAL_ERROR);
        }
    }

    /*
     * executes a query in cache and/or in the data bases, depending on the DB
     * choosen and cache's presence
     */
    private <T> Result<?> tryQuery(String query, String containerName, Class<T> clazz) {

        Result<List<T>> data;

            Log.info("Cache está ativa");
            try {
                byte[] dataOnCache = redisClient.get(String.valueOf(query.hashCode())).getBytes();

                if (dataOnCache == null) {

                    data = Result.ok(DB.sql(query, clazz));

                    if (data.isOK()) {
                        Log.info("Foi buscar os objetos à DB e colocou na cache");
                        redisClient.setex(String.valueOf(query.hashCode()).getBytes(), 20, serialize(data.value()));
                    }
                } else {
                    data = Result.ok(deserializeList(dataOnCache, clazz));
                    Log.info("Obteve objeto da cache");
                }

            } catch (Exception e) {
                e.printStackTrace();
                return Result.error(ErrorCode.INTERNAL_ERROR);
            }

        return data;
    }

    /*
     * retrieves an object from cache
     */
    private Result<Short> getFromCache(String id, Result<List<Long>> like) {

        Result<Short> shortRes;

        try {
            String dataOnCache = redisClient.get(id);

            if (dataOnCache != null) {
                Log.info("Obteve objeto da cache");
                shortRes = errorOrValue(parseShortFromString(dataOnCache),
                        shrt -> shrt.copyWithLikes_And_Token(like.value().get(0)));
            } else {

                shortRes = errorOrValue(DB.getOne(id, Short.class),
                        shrt -> shrt.copyWithLikes_And_Token(like.value().get(0)));


                Short item = shortRes.value();
                Log.info("%%%%%%%%%%%%%%%%%%% foi buscar ao cosmos " + item);
                this.putInCache(id, item.toString());
                Log.info("&&&&&&&&&&&&&&&&&& colocou no jedis");
            }

        } catch (Exception e) {
            e.printStackTrace();
            return Result.error(ErrorCode.INTERNAL_ERROR);
        }

        return shortRes;
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
