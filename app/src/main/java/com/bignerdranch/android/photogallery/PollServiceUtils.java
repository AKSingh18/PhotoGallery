package com.bignerdranch.android.photogallery;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.util.List;

public class PollServiceUtils
{
    private static final String TAG = "PollServiceUtils";

    private static final String NEW_RESULT_CHANNEL = "NEW_RESULT";

    public static final String ACTION_SHOW_NOTIFICATION = "com.bignerdranch.android.photogallery.SHOW_NOTIFICATION";
    public static final String PERM_PRIVATE ="com.bignerdranch.android.photogallery.PRIVATE";
    public static final String REQUEST_CODE = "REQUEST_CODE";
    public static final String NOTIFICATION = "NOTIFICATION";

    // Code source: https://developer.android.com/training/notify-user/channels#CreateChannel
    private static void createNotificationChannel(Context context)
    {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        {
            NotificationManager notificationManager = context
                    .getSystemService(NotificationManager.class);

            // check if the channel already exists or not
            if (notificationManager.getNotificationChannel(NEW_RESULT_CHANNEL) != null) return;

            CharSequence name = context.getString(R.string.channel_name);
            String description = context.getString(R.string.channel_description);
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(NEW_RESULT_CHANNEL, name, importance);
            channel.setDescription(description);

            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            notificationManager.createNotificationChannel(channel);
        }
    }

    public static void startPollService(Context context)
    {
        boolean isOn = QueryPreferences.isPollServiceOn(context);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
        {
            PollJobService.scheduleJob(context, isOn);
        }
        else PollIntentService.setServiceAlarm(context, isOn);
    }

    public static boolean isPollServiceOn(Context context)
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
        {
            return PollJobService.isJobScheduled(context);
        }
        else
        {
            return PollIntentService.isServiceAlarmOn(context);
        }
    }

    public static void showBackgroundNotification(Context context, int requestCode, Notification notification)
    {
        Intent i = new Intent(ACTION_SHOW_NOTIFICATION);

        Log.i(TAG, "showBackgroundNotification: context.getPackageName() = " + context.getPackageName());
        i.setPackage(context.getPackageName());

        i.putExtra(REQUEST_CODE, requestCode);
        i.putExtra(NOTIFICATION, notification);

        context.sendOrderedBroadcast(i, PERM_PRIVATE, null, null,
                Activity.RESULT_OK, null, null);
    }

    public static void pollFlicker(Context context)
    {
        String query = QueryPreferences.getStoredQuery(context);
        String lastResultId = QueryPreferences.getLastResultId(context);

        List<GalleryItem> items;

        if (query == null) items = new FlickrFetchr().fetchRecentPhotos(0);
        else items = new FlickrFetchr().searchPhotos(query, 0);

        if (items.size() == 0) return;

        String resultId = items.get(0).getId();

        if (resultId.equals(lastResultId))
        {
            Log.i(TAG, "Got an old result: " + resultId);
        }
        else
        {
            Log.i(TAG, "Got a new result: " + resultId);

            createNotificationChannel(context);

            Resources resources = context.getResources();

            Intent i = PhotoGalleryActivity.newIntent(context);
            PendingIntent pi;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            {
                pi = PendingIntent.getActivity(context, 0, i, PendingIntent.FLAG_IMMUTABLE);
            }
            else
            {
                pi = PendingIntent.getActivity(context, 0, i, 0);
            }


            Notification notification = new NotificationCompat.Builder(context, NEW_RESULT_CHANNEL)
                    .setTicker(resources.getString(R.string.new_pictures_text))
                    .setSmallIcon(android.R.drawable.ic_menu_report_image)
                    .setContentTitle(resources.getString(R.string.new_pictures_title))
                    .setContentText(resources.getString(R.string.new_pictures_text))
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .build();

            PollServiceUtils.showBackgroundNotification(context,0, notification);
        }

        QueryPreferences.setLastResultId(context, resultId);
    }
}
