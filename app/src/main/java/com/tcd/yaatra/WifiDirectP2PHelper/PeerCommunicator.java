package com.tcd.yaatra.WifiDirectP2PHelper;

import android.content.IntentFilter;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
import android.util.Log;
import com.tcd.yaatra.repository.models.Gender;
import com.tcd.yaatra.repository.models.TravellerInfo;
import com.tcd.yaatra.repository.models.TravellerStatus;
import com.tcd.yaatra.repository.models.FellowTravellersCache;
import com.tcd.yaatra.ui.activities.PeerToPeerActivity;
import com.tcd.yaatra.utils.NetworkUtils;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import android.os.Handler;

import static android.content.Context.WIFI_P2P_SERVICE;

public class PeerCommunicator implements WifiP2pManager.ConnectionInfoListener {

    //region Private Variables

    WifiP2pManager wifiP2pManager;
    WifiP2pManager.Channel wifiP2pChannel;
    ServiceDiscoveryReceiver serviceDiscoveryReceiver;

    private static final String TAG = "PeerCommunicator";
    private static final String SERVICE_INSTANCE = "com.tcd.yaatra.WifiDirectService";
    private static final String SERVICE_TYPE = "tcp";

    private static final int SERVICE_BROADCASTING_INTERVAL = 11111;
    //private static final int SERVICE_DISCOVERING_INTERVAL = 10000;

    private PeerToPeerActivity peerToPeerActivity;

    private WifiP2pDnsSdServiceRequest serviceRequest;

    private boolean isReceiverRegistered = false;

    private boolean isDiscoveryStarted = false;

    private int appUserId;
    private String appUserName;

    private TravellerInfo currentUserTravellerInfo;

    private Handler serviceBroadcastingHandler;
    private Handler serviceDiscoveringHandler;

    //endregion

    public PeerCommunicator(PeerToPeerActivity activity, String appUserName){

        peerToPeerActivity = activity;
        this.appUserId = 1;
        this.appUserName = appUserName;

        initializeWiFiDirectComponents();

        NetworkUtils.deletePersistentGroups(wifiP2pManager, wifiP2pChannel);

        initializeUserAsTraveller();
    }

    private void initializeWiFiDirectComponents(){
        wifiP2pManager = (WifiP2pManager) peerToPeerActivity.getSystemService(WIFI_P2P_SERVICE);
        wifiP2pChannel = wifiP2pManager.initialize(peerToPeerActivity, peerToPeerActivity.getMainLooper(), null);
        serviceDiscoveryReceiver = new ServiceDiscoveryReceiver(wifiP2pManager, wifiP2pChannel, this);
        serviceBroadcastingHandler = new Handler();
        serviceDiscoveringHandler = new Handler();
    }

    private void initializeUserAsTraveller(){

        LocalDateTime now = LocalDateTime.now();
        currentUserTravellerInfo =
                new TravellerInfo(appUserId, appUserName, 20, Gender.NotSpecified
                        , 0.0d, 0.0d, 0.0d, 0.0d
                        , TravellerStatus.None, now, 0.0d
                        , NetworkUtils.getWiFiIPAddress(peerToPeerActivity)
                        , 12345, now, appUserName);
    }

    public void advertiseStatusAndDiscoverFellowTravellers(TravellerStatus status){

        setCurrentStatusOfAppUser(status);

        stopServiceDiscoveryTimer();

        wifiP2pManager.clearLocalServices(wifiP2pChannel, new WifiP2pManager.ActionListener() {

            @Override
            public void onSuccess() {

                Log.d(TAG, "Removed all existing wifi direct local services");

                advertiseStatus();
            }

            @Override
            public void onFailure(int error) {
                Log.e(TAG, "Error: Failed to remove existing wifi direct services");
            }
        });
    }

    private void advertiseStatus() {
        HashMap<Integer, TravellerInfo> allTravellers = FellowTravellersCache.getCacheInstance().getFellowTravellers(appUserName);
        allTravellers.put(appUserId, currentUserTravellerInfo);

        Map<String, String> serializedRecord = P2pSerializerDeserializer.serializeToMap(allTravellers.values());

        WifiP2pDnsSdServiceInfo service = WifiP2pDnsSdServiceInfo.newInstance(
                SERVICE_INSTANCE, SERVICE_TYPE, serializedRecord);

        wifiP2pManager.addLocalService(wifiP2pChannel, service, new WifiP2pManager.ActionListener() {

            @Override
            public void onSuccess() {
                Log.d(TAG, "Started advertising status of travellers");

                registerListenersForFellowTravellers();

                // service broadcasting started
                serviceBroadcastingHandler
                        .postDelayed(mServiceBroadcastingRunnable,
                                SERVICE_BROADCASTING_INTERVAL);
            }

            @Override
            public void onFailure(int error) {
                Log.e(TAG, "Error: Failed to advertise status");
            }
        });
    }

    private void setCurrentStatusOfAppUser(TravellerStatus status){
        currentUserTravellerInfo.setStatus(status);
    }

    private void subscribeStatusChangeOfPeers(){

        serviceRequest = WifiP2pDnsSdServiceRequest.newInstance();
        wifiP2pManager.removeServiceRequest(wifiP2pChannel, serviceRequest, new WifiP2pManager.ActionListener() {

            @Override
            public void onSuccess() {
                Log.d(TAG, "Removed existing service discovery request from framework");

                wifiP2pManager.addServiceRequest(wifiP2pChannel, serviceRequest,
                    new WifiP2pManager.ActionListener() {

                        @Override
                        public void onSuccess() {
                            Log.d(TAG, "Added service discovery request to framework");

                            startDiscoveringFellowTravellers();
                        }

                        @Override
                        public void onFailure(int arg0) {
                            Log.d(TAG, "Error: Failed to add service discovery request to framework");
                        }
                    });
            }

            @Override
            public void onFailure(int arg0) {
                Log.d(TAG, "Error: Failed to remove service discovery request from framework");
            }
        });
    }

    private void registerListenersForFellowTravellers() {
        wifiP2pManager.setDnsSdResponseListeners(wifiP2pChannel,
            new WifiP2pManager.DnsSdServiceResponseListener() {

                @Override
                public void onDnsSdServiceAvailable(String instanceName,
                                                    String registrationType, WifiP2pDevice srcDevice) {
                    Log.d(TAG, instanceName + "####" + registrationType);
                    // A service has been discovered. Is this our app?
                    if (instanceName.equalsIgnoreCase(SERVICE_INSTANCE)) {
                        // yes it is
                    } else {
                        //no it isn't
                    }
                }
            },
            new WifiP2pManager.DnsSdTxtRecordListener() {

                @Override
                public void onDnsSdTxtRecordAvailable(
                        String fullDomainName, Map<String, String> travellersInfoMap,
                        WifiP2pDevice device) {

                    //Check if published data is from our app service
                    if (fullDomainName.toLowerCase().startsWith(SERVICE_INSTANCE.toLowerCase())) {

                        Log.d(TAG, "Received advertised information from peers");

                        //Save or Update existing information about peer traveller
                        HashMap<Integer, TravellerInfo> fellowTravellers = P2pSerializerDeserializer.deserializeFromMap(travellersInfoMap);

                        HashMap<Integer, TravellerInfo> onlyPeerTravellers = new HashMap<>(fellowTravellers);
                        onlyPeerTravellers.remove(appUserId);

                        boolean isCacheUpdated = FellowTravellersCache.getCacheInstance().addOrUpdate(onlyPeerTravellers);

                        if(isCacheUpdated){
                            peerToPeerActivity.showFellowTravellers(FellowTravellersCache.getCacheInstance().getFellowTravellers(appUserName));

                            //Start advertising newly discovered/updated fellow travellers
                            advertiseStatusAndDiscoverFellowTravellers(currentUserTravellerInfo.getStatus());
                        }
                    }
                }
            }
        );
    }

    private void startDiscoveringFellowTravellers(){

        wifiP2pManager.discoverServices(wifiP2pChannel, new WifiP2pManager.ActionListener() {

            @Override
            public void onSuccess() {
                /*serviceDiscoveringHandler.postDelayed(
                        mServiceDiscoveringRunnable,
                        SERVICE_DISCOVERING_INTERVAL);*/
                Log.d(TAG, "Service discovery initiated");
            }

            @Override
            public void onFailure(int arg0) {
                Log.d(TAG, "Service discovery failed: " + arg0);
            }
        });

        isDiscoveryStarted = true;
    }

    private void stopServiceDiscoveryTimer(){
        if(isDiscoveryStarted){
            serviceBroadcastingHandler.removeCallbacksAndMessages(null);
            serviceDiscoveringHandler.removeCallbacksAndMessages(null);
            serviceBroadcastingHandler = new Handler();
            serviceDiscoveringHandler = new Handler();

            isDiscoveryStarted = false;
        }
    }

    public void registerPeerActivityListener(){
        IntentFilter wifiP2pFilter = new IntentFilter();
        wifiP2pFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        wifiP2pFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
        wifiP2pFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);

        serviceDiscoveryReceiver = new ServiceDiscoveryReceiver(wifiP2pManager,
                wifiP2pChannel, this);
        peerToPeerActivity.registerReceiver(serviceDiscoveryReceiver, wifiP2pFilter);

        isReceiverRegistered = true;
    }

    @Override
    public void onConnectionInfoAvailable(WifiP2pInfo wifiP2pInfo) {
    }

    public void cleanup(){

        stopServiceDiscoveryTimer();

        if(isReceiverRegistered) {
            peerToPeerActivity.unregisterReceiver(serviceDiscoveryReceiver);
            isReceiverRegistered = false;
        }

        wifiP2pManager.stopPeerDiscovery(wifiP2pChannel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Stopped peer discovery");
            }

            @Override
            public void onFailure(int reason) {
                Log.d(TAG, "Failed to stop peer discovery");
            }
        });

        wifiP2pManager.clearServiceRequests(wifiP2pChannel, new WifiP2pManager.ActionListener() {

            @Override
            public void onSuccess() {
                Log.d(TAG, "Removed service discovery request from framework");
            }

            @Override
            public void onFailure(int arg0) {
                Log.d(TAG, "Error: Failed to remove service discovery request from framework");
            }
        });

        wifiP2pManager.clearLocalServices(wifiP2pChannel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Removed all wifi direct local services");
            }

            @Override
            public void onFailure(int error) {
                Log.e(TAG, "Error: Failed to remove wifi direct services");
            }
        });


        serviceBroadcastingHandler = null;
        serviceDiscoveringHandler = null;
        serviceRequest = null;
        serviceDiscoveryReceiver = null;
        wifiP2pChannel.close();
        wifiP2pChannel = null;
        wifiP2pManager = null;
    }

    //region Discovery Runnable Tasks

    private Runnable mServiceBroadcastingRunnable = new Runnable() {
        @Override
        public void run() {

            wifiP2pManager.stopPeerDiscovery(wifiP2pChannel, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    Log.d(TAG, "Stopped peer discovery");

                    wifiP2pManager.discoverPeers(wifiP2pChannel, new WifiP2pManager.ActionListener() {
                        @Override
                        public void onSuccess() {

                            Log.d(TAG, "Started peer discovery");

                            subscribeStatusChangeOfPeers();
                        }

                        @Override
                        public void onFailure(int error) {

                            Log.d(TAG, "Failed to start peer discovery");
                        }
                    });
                }

                @Override
                public void onFailure(int reason) {
                    Log.d(TAG, "Failed to stop peer discovery");
                }
            });

            serviceBroadcastingHandler
                    .postDelayed(mServiceBroadcastingRunnable, SERVICE_BROADCASTING_INTERVAL);
        }
    };

    private Runnable mServiceDiscoveringRunnable = new Runnable() {
        @Override
        public void run() {
            subscribeStatusChangeOfPeers();
        }
    };

    //endregion
}
