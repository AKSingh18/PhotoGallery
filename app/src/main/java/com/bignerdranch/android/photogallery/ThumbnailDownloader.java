package com.bignerdranch.android.photogallery;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;
import android.util.LruCache;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ThumbnailDownloader<T> extends HandlerThread
{
    public interface ThumbnailDownloadListener<T>
    {
        void onThumbnailDownloaded(T target, Bitmap thumbnail);
    }

    private static final String TAG = "ThumbnailDownloader";
    private static final int MESSAGE_DOWNLOAD = 0;
    private static final int MESSAGE_PRELOAD = 1;

    private boolean mHasQuit = false;
    private Handler mRequestHandler;
    private Handler mResponseHandler;
    private ConcurrentMap<T, String> mRequestMap = new ConcurrentHashMap<>();

    // Code regarding initialization of Lru Cache has been copied from
    // https://developer.android.com/topic/performance/graphics/cache-bitmap#memory-cache
    final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);

    // Use 1/8th of the available memory for this memory cache.
    final int cacheSize = maxMemory / 8;

    LruCache<String, Bitmap> mLruCache = new LruCache<String, Bitmap>(cacheSize)
    {
        @Override
        protected int sizeOf(String key, Bitmap bitmap)
        {
            // The cache size will be measured in kilobytes rather than
            // number of items.
            return bitmap.getByteCount() / 1024;
        }
    };

    private ThumbnailDownloadListener<T> mThumbnailDownloadListener;

    public void setThumbnailDownloadListener(ThumbnailDownloadListener<T> listener)
    {
        mThumbnailDownloadListener = listener;
    }

    public ThumbnailDownloader(Handler responseHandler)
    {
        super(TAG);
        mResponseHandler = responseHandler;
    }

    @Override
    public boolean quit()
    {
        mHasQuit = true;
        return super.quit();
    }

    @Override
    protected void onLooperPrepared()
    {
        mRequestHandler = new Handler()
        {
            @Override
            public void handleMessage(@NonNull Message msg)
            {
                if (msg.what == MESSAGE_DOWNLOAD)
                {
                    T target = (T)msg.obj;
                    Log.i(TAG, "Got a request for URL: " + mRequestMap.get(target));
                    handleRequest(target);
                }
                else if (msg.what == MESSAGE_PRELOAD)
                {
                    String url = (String)msg.obj;
                    Log.i(TAG, "handleMessage: Got a request for pre-loading URL = " + url);
                    cacheImage(url);
                }
            }
        };
    }

    private void handleRequest(final T target)
    {
        final String url = mRequestMap.get(target);
        if (url == null) return;

        if (mLruCache.get(url) == null) cacheImage(url);

        mResponseHandler.post(new Runnable()
        {
            public void run()
            {
                if (mRequestMap.get(target) != url || mHasQuit) return;

                mRequestMap.remove(target);
                mThumbnailDownloadListener.onThumbnailDownloaded(target, mLruCache.get(url));
            }
        });
    }

    public void queueThumbnail(T target, String url)
    {
        Log.i(TAG, "Got a URL: " + url);

        if (url == null)  mRequestMap.remove(target);
        else
        {
            mRequestMap.put(target, url);
            mRequestHandler.obtainMessage(MESSAGE_DOWNLOAD, target).sendToTarget();
        }
    }

    public void preload(String url)
    {
        Log.i(TAG, "preload: Got a URL = " + url);

        if (url == null) return;

        if (mLruCache.get(url) == null)
        {
            mRequestHandler.obtainMessage(MESSAGE_PRELOAD, url).sendToTarget();
        }
    }

    private void cacheImage(String url)
    {
        try
        {
            byte[] bitmapBytes = new FlickrFetchr().getUrlBytes(url);

            final Bitmap bitmap = BitmapFactory.decodeByteArray(bitmapBytes, 0, bitmapBytes.length);
            Log.i(TAG, "Bitmap created");

            mLruCache.put(url, bitmap);
        }
        catch (IOException ioe)
        {
            Log.e(TAG, "Error downloading image", ioe);
        }
    }

    public Bitmap retrieveFromCache(String url)
    {
        return mLruCache.get(url);
    }

    public void clearQueue()
    {
        mRequestHandler.removeMessages(MESSAGE_DOWNLOAD);
        mRequestMap.clear();
    }
}
