package com.bignerdranch.android.photogallery;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import java.util.concurrent.TimeUnit;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class PollJobService extends JobService
{
    private static final String TAG = "PollJobService";

    private static final long POLL_INTERVAL_MS = TimeUnit.MINUTES.toMillis(15);
    private static final int POLL_JOB_ID = 1;

    private PollTask mCurrentTask;

    private class PollTask extends AsyncTask<JobParameters,Void,Void>
    {
        @Override
        protected Void doInBackground(JobParameters... params)
        {
            JobParameters jobParams = params[0];

            PollServiceUtils.pollFlicker(PollJobService.this);
            jobFinished(jobParams, false);

            return null;
        }
    }

    public static void scheduleJob(Context context, boolean shouldRun)
    {
        JobScheduler scheduler = (JobScheduler) context
                .getSystemService(Context.JOB_SCHEDULER_SERVICE);

        if (shouldRun)
        {
            JobInfo jobInfo = new JobInfo
                    .Builder(POLL_JOB_ID, new ComponentName(context, PollJobService.class))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                    .setPeriodic(POLL_INTERVAL_MS)
                    .setPersisted(true)
                    .build();

            scheduler.schedule(jobInfo);

            Log.i(TAG, "scheduleJob: poll job has been scheduled");
        }
        else
        {
            Log.i(TAG, "scheduleJob: poll job cancelled");
            scheduler.cancel(POLL_JOB_ID);
        }
    }

    public static boolean isJobScheduled(Context context)
    {
        JobScheduler scheduler = (JobScheduler) context
                .getSystemService(Context.JOB_SCHEDULER_SERVICE);

        boolean hasBeenScheduled = false;

        for (JobInfo jobInfo : scheduler.getAllPendingJobs())
        {
            if (jobInfo.getId() == POLL_JOB_ID)
            {
                hasBeenScheduled = true;
            }
        }

        Log.i(TAG, "isJobScheduled: hasBeenScheduled = " + hasBeenScheduled);

        return hasBeenScheduled;
    }

    @Override
    public boolean onStartJob(JobParameters params)
    {
        mCurrentTask = new PollTask();
        mCurrentTask.execute(params);

        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params)
    {
        if (mCurrentTask != null)
        {
            mCurrentTask.cancel(true);
        }

        return false;
    }
}
