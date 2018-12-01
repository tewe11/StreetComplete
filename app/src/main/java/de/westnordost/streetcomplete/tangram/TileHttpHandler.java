package de.westnordost.streetcomplete.tangram;

import com.mapzen.tangram.networking.DefaultHttpHandler;

import java.io.File;
import java.util.concurrent.TimeUnit;

import okhttp3.Cache;
import okhttp3.CacheControl;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.internal.Version;

public class TileHttpHandler extends DefaultHttpHandler
{
	private final String apiKey;
	private final String userAgent;
	private final File cacheDirectory;
	private final long cacheMaxSize;

	private final CacheControl tileCacheControl = new CacheControl.Builder().maxStale(7, TimeUnit.DAYS).build();

	public TileHttpHandler(String userAgent, String apiKey)
	{
		this(userAgent, apiKey, null, 0);
	}

	public TileHttpHandler(String userAgent, String apiKey, File directory, long maxSize)
	{
		this.userAgent = userAgent;
		this.cacheDirectory = directory;
		this.cacheMaxSize = maxSize;
		this.apiKey = apiKey;
	}

	@Override protected void configureClient(OkHttpClient.Builder builder)
	{
		if(cacheDirectory != null)
		{
			builder.cache(new Cache(cacheDirectory, cacheMaxSize));
		}
	}

	@Override protected void configureRequest(HttpUrl url, Request.Builder builder)
	{
		builder.cacheControl(tileCacheControl);
		if(apiKey != null)
		{
			builder.url(url.newBuilder().addQueryParameter("api_key", apiKey).build());
		}
		if(userAgent != null)
		{
			builder.header("User-Agent", userAgent + " / " + Version.userAgent());
		}
	}
}
