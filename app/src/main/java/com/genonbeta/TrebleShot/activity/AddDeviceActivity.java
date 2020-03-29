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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentFactory;
import androidx.fragment.app.FragmentTransaction;
import com.genonbeta.TrebleShot.R;
import com.genonbeta.TrebleShot.app.Activity;
import com.genonbeta.TrebleShot.fragment.BarcodeConnectFragment;
import com.genonbeta.TrebleShot.fragment.DeviceListFragment;
import com.genonbeta.TrebleShot.fragment.HotspotManagerFragment;
import com.genonbeta.TrebleShot.fragment.NetworkManagerFragment;
import com.genonbeta.TrebleShot.object.Device;
import com.genonbeta.TrebleShot.object.DeviceConnection;
import com.genonbeta.TrebleShot.service.BackgroundService;
import com.genonbeta.TrebleShot.ui.callback.TitleProvider;
import com.genonbeta.TrebleShot.ui.help.ConnectionSetUpAssistant;
import com.genonbeta.TrebleShot.util.AppUtils;
import com.genonbeta.android.framework.ui.callback.SnackbarPlacementProvider;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;

public class AddDeviceActivity extends Activity implements SnackbarPlacementProvider
{
    public static final String
            ACTION_CHANGE_FRAGMENT = "com.genonbeta.intent.action.CONNECTION_MANAGER_CHANGE_FRAGMENT",
            EXTRA_FRAGMENT_ENUM = "extraFragmentEnum",
            EXTRA_DEVICE_ID = "extraDeviceId",
            EXTRA_CONNECTION_ADAPTER = "extraConnectionAdapter";

    public static final int
            REQUEST_BARCODE_SCAN = 100,
            REQUEST_IP_DISCOVERY = 110;

    private final IntentFilter mFilter = new IntentFilter();
    private HotspotManagerFragment mHotspotManagerFragment;
    private NetworkManagerFragment mNetworkManagerFragment;
    private DeviceListFragment mDeviceListFragment;
    private OptionsFragment mOptionsFragment;
    private AppBarLayout mAppBarLayout;
    private CollapsingToolbarLayout mToolbarLayout;
    private ProgressBar mProgressBar;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            if (ACTION_CHANGE_FRAGMENT.equals(intent.getAction()) && intent.hasExtra(EXTRA_FRAGMENT_ENUM)) {
                String fragmentEnum = intent.getStringExtra(EXTRA_FRAGMENT_ENUM);

                try {
                    setFragment(AvailableFragment.valueOf(fragmentEnum));
                } catch (Exception e) {
                    // do nothing
                }
            } else if (BackgroundService.ACTION_DEVICE_ACQUAINTANCE.equals(intent.getAction())
                    && intent.hasExtra(BackgroundService.EXTRA_DEVICE_ID)
                    && intent.hasExtra(BackgroundService.EXTRA_CONNECTION_ADAPTER_NAME)) {
                Device device = new Device(intent.getStringExtra(BackgroundService.EXTRA_DEVICE_ID));
                DeviceConnection connection = new DeviceConnection(device.id, intent.getStringExtra(
                        BackgroundService.EXTRA_CONNECTION_ADAPTER_NAME));

                try {
                    AppUtils.getKuick(AddDeviceActivity.this).reconstruct(device);
                    AppUtils.getKuick(AddDeviceActivity.this).reconstruct(connection);
                    returnResult(AddDeviceActivity.this, device, connection);
                } catch (Exception e) {
                    e.printStackTrace();
                }

            } else if (BackgroundService.ACTION_INCOMING_TRANSFER_READY.equals(intent.getAction())
                    && intent.hasExtra(BackgroundService.EXTRA_GROUP_ID)) {
                ViewTransferActivity.startInstance(AddDeviceActivity.this,
                        intent.getLongExtra(BackgroundService.EXTRA_GROUP_ID, -1));
                finish();
            }
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        setResult(RESULT_CANCELED);
        setContentView(R.layout.activity_connection_manager);

        ArrayList<String> hiddenDeviceTypes = new ArrayList<>();
        hiddenDeviceTypes.add(Device.Type.WEB.toString());

        Bundle deviceListArgs = new Bundle();
        deviceListArgs.putStringArrayList(DeviceListFragment.ARG_HIDDEN_DEVICES_LIST,
                hiddenDeviceTypes);

        FragmentFactory factory = getSupportFragmentManager().getFragmentFactory();
        Toolbar toolbar = findViewById(R.id.toolbar);
        mAppBarLayout = findViewById(R.id.app_bar);
        mProgressBar = findViewById(R.id.activity_connection_establishing_progress_bar);
        mToolbarLayout = findViewById(R.id.toolbar_layout);
        mOptionsFragment = (OptionsFragment) factory.instantiate(getClassLoader(), OptionsFragment.class.getName());
        mHotspotManagerFragment = (HotspotManagerFragment) factory.instantiate(getClassLoader(),
                HotspotManagerFragment.class.getName());
        mNetworkManagerFragment = (NetworkManagerFragment) factory.instantiate(getClassLoader(),
                NetworkManagerFragment.class.getName());
        mDeviceListFragment = (DeviceListFragment) factory.instantiate(getClassLoader(),
                DeviceListFragment.class.getName());
        mDeviceListFragment.setArguments(deviceListArgs);

        mFilter.addAction(ACTION_CHANGE_FRAGMENT);
        mFilter.addAction(BackgroundService.ACTION_DEVICE_ACQUAINTANCE);
        mFilter.addAction(BackgroundService.ACTION_INCOMING_TRANSFER_READY);

        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null)
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        checkFragment();
        registerReceiver(mReceiver, mFilter);
    }

    @Override
    protected void onPause()
    {
        super.onPause();
        unregisterReceiver(mReceiver);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data)
    {
        if (resultCode == RESULT_OK && data != null)
            if (requestCode == REQUEST_BARCODE_SCAN) {
                Device device = data.getParcelableExtra(BarcodeScannerActivity.EXTRA_DEVICE);
                DeviceConnection connection = data.getParcelableExtra(BarcodeScannerActivity.EXTRA_CONNECTION);

                if (device != null && connection != null)
                    returnResult(this, device, connection);
            } else if (requestCode == REQUEST_IP_DISCOVERY) {
                // TODO: 23.03.2020 implement
            }

        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onBackPressed()
    {
        if (getShowingFragment() instanceof OptionsFragment)
            super.onBackPressed();
        else
            setFragment(AvailableFragment.Options);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        int id = item.getItemId();

        if (id == android.R.id.home)
            onBackPressed();
        else
            return super.onOptionsItemSelected(item);

        return true;
    }

    public void applyViewChanges(Fragment fragment)
    {
        boolean isOptions = fragment instanceof OptionsFragment;

        if (getSupportActionBar() != null) {
            mToolbarLayout.setTitle(fragment instanceof TitleProvider
                    ? ((TitleProvider) fragment).getDistinctiveTitle(AddDeviceActivity.this)
                    : getString(R.string.butn_addDevices));
        }

        mAppBarLayout.setExpanded(isOptions, true);
    }

    private void checkFragment()
    {
        Fragment currentFragment = getShowingFragment();

        if (currentFragment == null)
            setFragment(AvailableFragment.Options);
        else
            applyViewChanges(currentFragment);
    }

    @Override
    public Snackbar createSnackbar(int resId, Object... objects)
    {
        return Snackbar.make(findViewById(R.id.activity_connection_establishing_content_view),
                getString(resId, objects), Snackbar.LENGTH_LONG);
    }

    public AvailableFragment getShowingFragmentId()
    {
        Fragment fragment = getShowingFragment();

        if (fragment instanceof BarcodeConnectFragment)
            return AvailableFragment.ScanQrCode;
        else if (fragment instanceof HotspotManagerFragment)
            return AvailableFragment.CreateHotspot;
        else if (fragment instanceof NetworkManagerFragment)
            return AvailableFragment.UseExistingNetwork;
        else if (fragment instanceof DeviceListFragment)
            return AvailableFragment.UseKnownDevice;

        // Probably OptionsFragment
        return AvailableFragment.Options;
    }

    @Nullable
    public Fragment getShowingFragment()
    {
        return getSupportFragmentManager().findFragmentById(R.id.activity_connection_establishing_content_view);
    }

    public static void returnResult(android.app.Activity activity, Device device, DeviceConnection connection)
    {
        returnResult(activity, device.id, connection.adapterName);
    }

    public static void returnResult(android.app.Activity activity, String deviceId, String adapterName)
    {
        activity.setResult(RESULT_OK, new Intent()
                .putExtra(EXTRA_DEVICE_ID, deviceId)
                .putExtra(EXTRA_CONNECTION_ADAPTER, adapterName));

        activity.finish();
    }

    public void setFragment(AvailableFragment fragment)
    {
        @Nullable
        Fragment activeFragment = getShowingFragment();
        Fragment fragmentCandidate;

        switch (fragment) {
            case EnterIpAddress:
                startIpConnectivity();
                return;
            case ScanQrCode:
                startCodeScanner();
                return;
            case CreateHotspot:
                fragmentCandidate = mHotspotManagerFragment;
                break;
            case UseExistingNetwork:
                fragmentCandidate = mNetworkManagerFragment;
                break;
            case UseKnownDevice:
                fragmentCandidate = mDeviceListFragment;
                break;
            case Options:
            default:
                fragmentCandidate = mOptionsFragment;
        }

        if (activeFragment == null || fragmentCandidate != activeFragment) {
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();

            if (activeFragment != null)
                transaction.remove(activeFragment);

            if (activeFragment != null && fragmentCandidate instanceof OptionsFragment)
                transaction.setCustomAnimations(R.anim.enter_from_left, R.anim.exit_to_right);
            else
                transaction.setCustomAnimations(R.anim.enter_from_right, R.anim.exit_to_left);

            transaction.add(R.id.activity_connection_establishing_content_view, fragmentCandidate);
            transaction.commit();

            applyViewChanges(fragmentCandidate);
        }
    }

    private void startCodeScanner()
    {
        startActivityForResult(new Intent(this, BarcodeScannerActivity.class), REQUEST_BARCODE_SCAN);
    }

    protected void startIpConnectivity()
    {
        startActivityForResult(new Intent(this, IpAddressConnectionActivity.class), REQUEST_IP_DISCOVERY);
    }

    public enum AvailableFragment
    {
        Options,
        UseExistingNetwork,
        UseKnownDevice,
        ScanQrCode,
        CreateHotspot,
        EnterIpAddress
    }

    public static class OptionsFragment extends com.genonbeta.android.framework.app.Fragment
    {
        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                                 @Nullable Bundle savedInstanceState)
        {
            View view = inflater.inflate(R.layout.layout_connection_options_fragment, container, false);

            View.OnClickListener listener = v -> {
                switch (v.getId()) {
                    case R.id.connection_option_devices:
                        updateFragment(AvailableFragment.UseKnownDevice);
                        break;
                    case R.id.connection_option_hotspot:
                        updateFragment(AvailableFragment.CreateHotspot);
                        break;
                    case R.id.connection_option_network:
                        updateFragment(AvailableFragment.UseExistingNetwork);
                        break;
                    case R.id.connection_option_manual_ip:
                        updateFragment(AvailableFragment.EnterIpAddress);
                        break;
                    case R.id.connection_option_scan:
                        updateFragment(AvailableFragment.ScanQrCode);
                }
            };

            view.findViewById(R.id.connection_option_devices).setOnClickListener(listener);
            view.findViewById(R.id.connection_option_hotspot).setOnClickListener(listener);
            view.findViewById(R.id.connection_option_network).setOnClickListener(listener);
            view.findViewById(R.id.connection_option_scan).setOnClickListener(listener);
            view.findViewById(R.id.connection_option_manual_ip).setOnClickListener(listener);

            view.findViewById(R.id.connection_option_guide).setOnClickListener(v ->
                    new ConnectionSetUpAssistant(getActivity()).startShowing());

            return view;
        }

        public void updateFragment(AvailableFragment fragment)
        {
            if (getContext() != null)
                getContext().sendBroadcast(new Intent(ACTION_CHANGE_FRAGMENT)
                        .putExtra(EXTRA_FRAGMENT_ENUM, fragment.toString()));
        }
    }
}
