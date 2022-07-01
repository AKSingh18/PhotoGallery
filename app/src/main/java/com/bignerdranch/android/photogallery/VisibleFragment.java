package com.bignerdranch.android.photogallery;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import androidx.fragment.app.Fragment;

public abstract class VisibleFragment extends Fragment
{
    private static final String TAG = "VisibleFragment";

    private BroadcastReceiver mOnShowNotification = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            // If we receive this, we're visible, so cancel the notification
            Log.i(TAG, "canceling notification");
            setResultCode(Activity.RESULT_CANCELED);
        }
    };

    @Override
    public void onStart()
    {
        super.onStart();

        IntentFilter filter = new IntentFilter(PollServiceUtils.ACTION_SHOW_NOTIFICATION);
        getActivity()
                .registerReceiver(mOnShowNotification, filter, PollServiceUtils.PERM_PRIVATE, null);
    }

    @Override
    public void onStop()
    {
        super.onStop();

        getActivity().unregisterReceiver(mOnShowNotification);
    }
}
