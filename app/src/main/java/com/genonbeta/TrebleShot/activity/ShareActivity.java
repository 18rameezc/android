/*
 * Copyright (C) 2019 Veli Tasalı
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package com.genonbeta.TrebleShot.activity;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import com.genonbeta.TrebleShot.R;
import com.genonbeta.TrebleShot.app.Activity;
import com.genonbeta.TrebleShot.io.Containable;
import com.genonbeta.TrebleShot.service.WorkerService;
import com.genonbeta.TrebleShot.task.OrganizeSharingRunningTask;
import com.genonbeta.TrebleShot.util.FileUtils;
import com.genonbeta.android.framework.io.DocumentFile;
import com.genonbeta.android.framework.ui.callback.SnackbarPlacementProvider;
import com.google.android.material.snackbar.Snackbar;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

public class ShareActivity extends Activity implements SnackbarPlacementProvider, Activity.OnPreloadArgumentWatcher,
        WorkerService.OnAttachListener
{
    public static final String TAG = "ShareActivity";

    public static final String
            ACTION_SEND = "genonbeta.intent.action.TREBLESHOT_SEND",
            ACTION_SEND_MULTIPLE = "genonbeta.intent.action.TREBLESHOT_SEND_MULTIPLE",
            EXTRA_DEVICE_ID = "extraDeviceId";

    private Bundle mPreLoadingBundle = new Bundle();
    private ProgressBar mProgressBar;
    private TextView mProgressTextLeft;
    private TextView mProgressTextRight;
    private TextView mTextMain;
    private List<Uri> mFileUris;
    private OrganizeSharingRunningTask mTask;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_share);

        String action = getIntent() != null ? getIntent().getAction() : null;

        if (ACTION_SEND.equals(action) || ACTION_SEND_MULTIPLE.equals(action) || Intent.ACTION_SEND.equals(action)
                || Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            if (getIntent().hasExtra(Intent.EXTRA_TEXT)) {
                startActivity(new Intent(ShareActivity.this, TextEditorActivity.class)
                        .setAction(TextEditorActivity.ACTION_EDIT_TEXT)
                        .putExtra(TextEditorActivity.EXTRA_TEXT_INDEX, getIntent().getStringExtra(Intent.EXTRA_TEXT)));
                finish();
            } else {
                List<Uri> fileUris = new ArrayList<>();

                if (ACTION_SEND_MULTIPLE.equals(action) || Intent.ACTION_SEND_MULTIPLE.equals(action)) {
                    List<Uri> pendingFileUris = getIntent().getParcelableArrayListExtra(Intent.EXTRA_STREAM);
                    if (pendingFileUris != null)
                        fileUris.addAll(pendingFileUris);
                } else {
                    fileUris.add(getIntent().getParcelableExtra(Intent.EXTRA_STREAM));
                }

                if (fileUris.size() == 0) {
                    Toast.makeText(this, R.string.mesg_nothingToShare, Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    mProgressBar = findViewById(R.id.progressBar);
                    mProgressTextLeft = findViewById(R.id.text1);
                    mProgressTextRight = findViewById(R.id.text2);
                    mTextMain = findViewById(R.id.textMain);

                    findViewById(R.id.cancelButton).setOnClickListener(v -> {
                        if (mTask != null)
                            mTask.getInterrupter().interrupt(true);
                    });

                    mFileUris = fileUris;

                    checkForTasks();
                }
            }
        } else {
            Toast.makeText(this, R.string.mesg_formatNotSupported, Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    @Override
    public void onAttachedToTask(WorkerService.BaseAttachableRunningTask task)
    {

    }

    @Override
    protected void onPreviousRunningTask(@Nullable WorkerService.BaseAttachableRunningTask task)
    {
        super.onPreviousRunningTask(task);

        if (task instanceof OrganizeSharingRunningTask) {
            mTask = ((OrganizeSharingRunningTask) task);
            mTask.setAnchorListener(this);
        } else {
            mTask = new OrganizeSharingRunningTask(mFileUris);

            mTask.setAnchorListener(this)
                    .setTitle(getString(R.string.mesg_organizingFiles))
                    .setContentIntent(this, getIntent())
                    .run(this);

            attachRunningTask(mTask);
        }
    }

    public Snackbar createSnackbar(int resId, Object... objects)
    {
        return Snackbar.make(getWindow().getDecorView(), getString(resId, objects), Snackbar.LENGTH_LONG);
    }

    public ProgressBar getProgressBar()
    {
        return mProgressBar;
    }

    @Override
    public Bundle passPreLoadingArguments()
    {
        return mPreLoadingBundle;
    }

    public void updateProgress(final int total, final int current)
    {
        if (isFinishing())
            return;

        runOnUiThread(() -> {
            mProgressTextLeft.setText(String.valueOf(current));
            mProgressTextRight.setText(String.valueOf(total));
        });

        mProgressBar.setProgress(current);
        mProgressBar.setMax(total);
    }

    public void updateText(WorkerService.RunningTask runningTask, final String text)
    {
        if (isFinishing())
            return;

        runningTask.publishStatusText(text);

        runOnUiThread(() -> mTextMain.setText(text));
    }
}

