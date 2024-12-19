package tukano.api;

import java.util.logging.Logger;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import tukano.impl.Token;
import tukano.auth.CookieStore;
import static tukano.auth.CookieStore.get;

/**
 * Represents a Short video uploaded by an user.
 * 
 * A short has an unique shortId and is owned by a given user;
 * Comprises of a short video, stored as a binary blob at some bloburl;.
 * A post also has a number of likes, which can increase or decrease over time.
 * It is the only piece of information that is mutable.
 * A short is timestamped when it is created.
 *
 */
@Table( name = "\"short\"")
@Entity
public class Short {

	private static Logger Log = Logger.getLogger(Short.class.getName());

	@Id
	@JsonProperty("id")
	String shortId;
	String ownerId;
	String blobUrl;
	long timestamp;
	int totalLikes;

	public Short() {
	}

	public Short(String shortId, String ownerId, String blobUrl, long timestamp, int totalLikes) {
		super();
		this.shortId = shortId;
		this.ownerId = ownerId;
		this.blobUrl = blobUrl;
		this.timestamp = timestamp;
		this.totalLikes = totalLikes;
	}

	public Short(String shortId, String ownerId, String blobUrl) {
		this(shortId, ownerId, blobUrl, System.currentTimeMillis(), 0);
	}

	public String getShortId() {
		return shortId;
	}

	public void setShortId(String shortId) {
		this.shortId = shortId;
	}

	public String getOwnerId() {
		return ownerId;
	}

	public void setOwnerId(String ownerId) {
		this.ownerId = ownerId;
	}

	public String getBlobUrl() {
		return blobUrl;
	}

	public void setBlobUrl(String blobUrl) {
		this.blobUrl = blobUrl;
	}

	public long getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(long timestamp) {
		this.timestamp = timestamp;
	}

	public int getTotalLikes() {
		return totalLikes;
	}

	public void setTotalLikes(int totalLikes) {
		this.totalLikes = totalLikes;
	}

	@Override
	public String toString() {
		return "Short [shortId=" + shortId + ", ownerId=" + ownerId + ", blobUrl=" + blobUrl + ", timestamp="
				+ timestamp + ", totalLikes=" + totalLikes + "]";
	}

	public Short copyWithLikes_And_Token(long totLikes) {
		var urlWithToken = String.format("%s?token=%s", blobUrl, CookieStore.getInstance().get(ownerId));
		System.out.println("####################### cookie no copy with likes " + CookieStore.getInstance().get(ownerId));
        Log.warning("Ou a criar o short " + urlWithToken);
        return new Short(shortId, ownerId, urlWithToken, timestamp, (int) totLikes);
    }
}