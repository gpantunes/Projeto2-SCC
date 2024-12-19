package tukano.impl;

import static java.lang.String.format;
import static tukano.api.Result.error;
import static tukano.api.Result.ok;
import static tukano.api.Result.ErrorCode.FORBIDDEN;
import tukano.auth.CookieStore;
import java.util.logging.Logger;
import java.util.*;

import tukano.api.Blobs;
import tukano.api.Result;
import tukano.impl.rest.TukanoRestServer;
import tukano.impl.storage.BlobStorage;
import tukano.impl.storage.FilesystemStorage;
import tukano.impl.clients.ShortsClient;
import utils.Hash;
import utils.Hex;

public class JavaBlobs implements Blobs {

	private static Blobs instance;
	private static Logger Log = Logger.getLogger(JavaBlobs.class.getName());
	private static ShortsClient shortsClient = new ShortsClient("http://user-shorts-service:80/tukano-1.0/rest");

	public String baseURI;
	private BlobStorage storage;

	synchronized public static Blobs getInstance() {
		if (instance == null)
			instance = new JavaBlobs();
		return instance;
	}

	private JavaBlobs() {
		storage = new FilesystemStorage();
		baseURI = String.format("%s/%s/", TukanoRestServer.serverURI, Blobs.NAME);
	}

	@Override
	public Result<Void> setCookie(String token, String userId) {
		String cookie = token + ";" + System.currentTimeMillis();

		var cookieStore = CookieStore.getInstance();
		cookieStore.set(userId, cookie);

		System.out.println("Cookie " + cookie);

		return Result.ok();
	}

	@Override
	public Result<Void> upload(String blobId, byte[] bytes, String token) {
		Log.info(() -> format("upload : blobId = %s, sha256 = %s, token = %s\n", blobId, Hex.of(Hash.sha256(bytes)),
				token));

		String userId = blobId.split("\\+")[0];
		if(!validSession(userId, token)) {
			return error(FORBIDDEN);
		}

		System.out.println("A cookie " + token + " é valida");

		return storage.write(toPath(blobId), bytes);
	}

	@Override
	public Result<byte[]> download(String blobId, String token) {
		Log.info(() -> format("download : blobId = %s, token=%s\n", blobId, token));

		String userId = blobId.split("\\+")[0];
		if(!validSession(userId, token)) {
			return error(FORBIDDEN);
		}

		System.out.println("A cookie " + token + " é valida");

		return storage.read(toPath(blobId));
	}

	@Override
	public Result<Void> delete(String blobId, String token) {
		Log.info(() -> format("delete : blobId = %s, token=%s\n", blobId, token));

		if (!validBlobId(blobId, token))
			return error(FORBIDDEN);

		Log.info("Acabou de apagar um blob");
		return storage.delete(toPath(blobId));
	}

	@Override
	public Result<Void> deleteAllBlobs(String userId, String token) {
		Log.info(() -> format("deleteAllBlobs : userId = %s, token=%s\n", userId, token));

		if (!validBlobId(userId, token))
			return error(FORBIDDEN);


		List<String> shortList = null;
		try {
			shortList = shortsClient.getShorts(userId).value();
		} catch (Exception e) {
			e.printStackTrace();
		}

		for (String shrt : shortList) {
			Log.info("Está a apagar o blob: " + shrt);
			String shortId = shrt.substring(shrt.indexOf("ShortId: ") + 9, shrt.indexOf(" TotalLikes:"));
			storage.delete(toPath(shortId.trim()));
		}

		Log.info("Acabou de apagar os blobs");
		return ok();
	}

	private boolean validBlobId(String blobId, String token) {
		//return Token.isValid(token, blobId);
		return true;
	}

	private boolean validSession(String userId, String cookie) {
		return CookieStore.getInstance().validateCookie(userId, cookie);
	}

	private String toPath(String blobId) {
		return blobId.replace("+", "/");
	}

}
