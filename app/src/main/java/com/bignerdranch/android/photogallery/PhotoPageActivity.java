package com.bignerdranch.android.photogallery;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.fragment.app.Fragment;

public class PhotoPageActivity extends SingleFragmentActivity
{
    public static Intent newIntent(Context context, Uri photoPageUri)
    {
        Intent intent = new Intent(context, PhotoPageActivity.class);
        intent.setData(photoPageUri);

        return intent;
    }

    @Override
    protected Fragment createFragment()
    {
        return PhotoPageFragment.newInstance(getIntent().getData());
    }

    @Override
    public void onBackPressed()
    {
        PhotoPageFragment fragment = (PhotoPageFragment)getSupportFragmentManager()
                .findFragmentById(R.id.fragment_container);

        if (fragment == null || !fragment.webViewGoBack()) super.onBackPressed();
    }
}
