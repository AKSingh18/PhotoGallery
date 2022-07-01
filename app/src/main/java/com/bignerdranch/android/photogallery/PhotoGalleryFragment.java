package com.bignerdranch.android.photogallery;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SearchView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class PhotoGalleryFragment extends VisibleFragment
{
    private static final String TAG = "PhotoGalleryFragment";

    private static final int COLUMN_WIDTH = 300;

    private RecyclerView mPhotoRecyclerView;
    private PhotoAdapter mPhotoAdapter;
    private GridLayoutManager mGridLayoutManager;
    private ProgressBar mProgressBar;

    private List<GalleryItem> mItems = new ArrayList<>();
    private ThumbnailDownloader<PhotoHolder> mThumbnailDownloader;

    private int mRecentPhotosPageCount;
    private int mQueryPhotosPageCount;

    private class FetchItemsTask extends AsyncTask<Void, Void, List<GalleryItem>>
    {
        private String mQuery;

        public FetchItemsTask(String query)
        {
            mQuery = query;
        }

        @Override protected List<GalleryItem> doInBackground(Void... params)
        {
            if (mQuery == null) return new FlickrFetchr().fetchRecentPhotos(++mRecentPhotosPageCount);
            else return new FlickrFetchr().searchPhotos(mQuery, ++mQueryPhotosPageCount);
        }

        @Override protected void onPostExecute(List<GalleryItem> items)
        {
            int fromPosition = mItems.size();
            mItems.addAll(items);

            updateUI(fromPosition);
            mProgressBar.setVisibility(View.GONE);
        }
    }

    private class PhotoHolder extends RecyclerView.ViewHolder implements View.OnClickListener
    {
        private ImageView mItemImageView;
        private GalleryItem mGalleryItem;

        public PhotoHolder(View itemView)
        {
            super(itemView);

            mItemImageView = (ImageView) itemView.findViewById(R.id.item_image_view);
            itemView.setOnClickListener(this);
        }

        public void bindDrawable(Drawable drawable)
        {
            mItemImageView.setImageDrawable(drawable);
        }

        public void bindGalleryItem(GalleryItem galleryItem)
        {
            mGalleryItem = galleryItem;
        }

        @Override
        public void onClick(View v)
        {
            Intent i = PhotoPageActivity.newIntent(getActivity(), mGalleryItem.getPhotoPageUri());
            startActivity(i);
        }
    }

    private class PhotoAdapter extends RecyclerView.Adapter<PhotoHolder>
    {
        private List<GalleryItem> mGalleryItems;

        public PhotoAdapter(List<GalleryItem> galleryItems)
        {
            mGalleryItems = galleryItems;
        }

        @Override
        public PhotoHolder onCreateViewHolder(ViewGroup viewGroup, int viewType)
        {
            LayoutInflater inflater = LayoutInflater.from(getActivity());

            View view = inflater.inflate(R.layout.list_item_gallery, viewGroup, false);
            return new PhotoHolder(view);
        }

        @Override
        public void onBindViewHolder(PhotoHolder photoHolder, int position)
        {
            GalleryItem galleryItem = mGalleryItems.get(position);

            Bitmap bitmap = mThumbnailDownloader.retrieveFromCache(galleryItem.getUrl());

            if (bitmap != null)
            {
                Drawable drawable = new BitmapDrawable(getResources(), bitmap);
                photoHolder.bindDrawable(drawable);
            }
            else mThumbnailDownloader.queueThumbnail(photoHolder, galleryItem.getUrl());

            photoHolder.bindGalleryItem(galleryItem);

            if (position == mGridLayoutManager.findLastVisibleItemPosition()) preloadImages();
        }

        @Override public int getItemCount()
        {
            return mGalleryItems.size();
        }
    }

    public static Fragment newInstance()
    {
        return new PhotoGalleryFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        setHasOptionsMenu(true);
        setRetainInstance(true);

        mRecentPhotosPageCount = 0;
        mQueryPhotosPageCount = 0;

        PollServiceUtils.startPollService(getActivity());

        updateItems();

        Handler responseHandler = new Handler();

        mThumbnailDownloader = new ThumbnailDownloader<>(responseHandler);
        mThumbnailDownloader.start();
        mThumbnailDownloader.getLooper();

        mThumbnailDownloader.setThumbnailDownloadListener(new ThumbnailDownloader.ThumbnailDownloadListener<PhotoHolder>()
        {
            @Override
            public void onThumbnailDownloaded(PhotoHolder photoHolder, Bitmap bitmap)
            {
                Drawable drawable = new BitmapDrawable(getResources(), bitmap);
                photoHolder.bindDrawable(drawable);
            }
        });

        Log.i(TAG, "onCreate: Background thread started");
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState)
    {
        View v = inflater.inflate(R.layout.fragment_photo_gallery, container, false);

        mPhotoRecyclerView = (RecyclerView)v.findViewById(R.id.photo_recycler_view);

        mPhotoRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener()
        {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy)
            {
                super.onScrolled(recyclerView, dx, dy);

                // Code source: https://stackoverflow.com/a/46342525/13618871
                if (!recyclerView.canScrollVertically(1) && mItems.size() != 0)
                {
                    updateItems();
                }
            }
        });

        mPhotoRecyclerView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener()
        {
            @Override
            public void onGlobalLayout()
            {
                int numberOfColumns = mPhotoRecyclerView.getWidth()/COLUMN_WIDTH;

                mGridLayoutManager = new GridLayoutManager(getActivity(), numberOfColumns);

                mPhotoRecyclerView.setLayoutManager(mGridLayoutManager);
                mPhotoRecyclerView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
            }
        });

        mProgressBar = v.findViewById(R.id.progress_bar);

        setupAdapter();

        return v;
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater)
    {
        super.onCreateOptionsMenu(menu, inflater);

        inflater.inflate(R.menu.fragment_photo_gallery, menu);

        MenuItem searchItem = (MenuItem)menu.findItem(R.id.menu_item_search);

        final SearchView searchView = (SearchView) searchItem.getActionView();

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener()
        {
            @Override
            public boolean onQueryTextSubmit(String query)
            {
                Log.i(TAG, "onQueryTextSubmit: " + query);

                QueryPreferences.setStoredQuery(getActivity(), query);

                // Collapse search view
                // Code source: https://stackoverflow.com/a/13692793/13618871
                searchView.onActionViewCollapsed();

                mProgressBar.setVisibility(View.VISIBLE);

                resetUI();
                updateItems();

                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText)
            {
                Log.i(TAG, "onQueryTextChange: " + newText);
                return false;
            }
        });

        searchView.setOnSearchClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                String query = QueryPreferences.getStoredQuery(getActivity());
                searchView.setQuery(query, false);
            }
        });

        MenuItem toggleItem = menu.findItem(R.id.menu_item_toggle_polling);

        if (PollServiceUtils.isPollServiceOn(getActivity()))
        {
            toggleItem.setTitle(R.string.stop_polling);
        }
        else
        {
            toggleItem.setTitle(R.string.start_polling);
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item)
    {
        if (item.getItemId() == R.id.menu_item_clear)
        {
            QueryPreferences.setStoredQuery(getActivity(), null);

            mProgressBar.setVisibility(View.VISIBLE);

            resetUI();
            updateItems();

            return true;
        }
        else if (item.getItemId() == R.id.menu_item_toggle_polling)
        {
            boolean shouldJobRun = !PollServiceUtils.isPollServiceOn(getActivity());
            QueryPreferences.setPollServiceOn(getActivity(), shouldJobRun);

            getActivity().invalidateOptionsMenu();
            PollServiceUtils.startPollService(getActivity());

            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void setupAdapter()
    {
        if (isAdded())
        {
            if (mPhotoAdapter == null) mPhotoAdapter = new PhotoAdapter(mItems);
            mPhotoRecyclerView.setAdapter(mPhotoAdapter);
        }
    }

    private void resetUI()
    {
        int size = mItems.size();

        mRecentPhotosPageCount = mQueryPhotosPageCount = 0;

        mItems.clear();
        mPhotoAdapter.notifyItemRangeRemoved(0, size);
    }

    private void updateUI(int fromPosition)
    {
        if (isAdded() && mPhotoAdapter != null)
        {
            mPhotoAdapter.notifyItemRangeInserted(fromPosition, mItems.size()-fromPosition+1);
        }
    }

    private void updateItems()
    {
        String query = QueryPreferences.getStoredQuery(getActivity());
        new FetchItemsTask(query).execute();
    }

    private void preloadImages()
    {
        int firstItemPosition = mGridLayoutManager.findFirstVisibleItemPosition();
        int lastItemPosition = mGridLayoutManager.findLastVisibleItemPosition();

        int i;
        GalleryItem item;

        i = Math.max(0, firstItemPosition-10);

        while (i <= firstItemPosition)
        {
            item = mItems.get(i);
            mThumbnailDownloader.preload(item.getUrl());
            i++;
        }

        i = lastItemPosition;
        while (i < mPhotoAdapter.getItemCount() && i <= lastItemPosition+10)
        {
            item = mItems.get(i);
            mThumbnailDownloader.preload(item.getUrl());
            i++;
        }
    }

    @Override
    public void onDestroyView()
    {
        super.onDestroyView();
        mThumbnailDownloader.clearQueue();
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();

        mThumbnailDownloader.quit();
        Log.i(TAG, "onDestroy: Background thread destroyed");
    }
}
