package com.example.dell.tracktest;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;

import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.baidu.location.BDLocation;
import com.baidu.location.BDLocationListener;
import com.baidu.location.LocationClient;
import com.baidu.location.LocationClientOption;
import com.baidu.mapapi.SDKInitializer;
import com.baidu.mapapi.map.BaiduMap;
import com.baidu.mapapi.map.BitmapDescriptor;
import com.baidu.mapapi.map.BitmapDescriptorFactory;
import com.baidu.mapapi.map.MapStatusUpdate;
import com.baidu.mapapi.map.MapStatusUpdateFactory;
import com.baidu.mapapi.map.MapView;
import com.baidu.mapapi.map.MarkerOptions;
import com.baidu.mapapi.map.MyLocationConfiguration;
import com.baidu.mapapi.map.MyLocationData;
import com.baidu.mapapi.map.OverlayOptions;
import com.baidu.mapapi.map.PolylineOptions;
import com.baidu.mapapi.model.LatLng;
import com.baidu.mapapi.search.core.SearchResult;
import com.baidu.mapapi.search.geocode.GeoCodeResult;
import com.baidu.mapapi.search.geocode.GeoCoder;
import com.baidu.mapapi.search.geocode.OnGetGeoCoderResultListener;
import com.baidu.mapapi.search.geocode.ReverseGeoCodeOption;
import com.baidu.mapapi.search.geocode.ReverseGeoCodeResult;
import com.baidu.trace.api.track.DistanceRequest;
import com.baidu.trace.api.track.DistanceResponse;
import com.baidu.trace.api.track.HistoryTrackRequest;
import com.baidu.trace.api.track.HistoryTrackResponse;
import com.baidu.trace.api.track.SupplementMode;
import com.baidu.trace.model.ProcessOption;
import com.baidu.trace.model.PushMessage;
import com.baidu.trace.model.TransportMode;
import com.google.android.gms.appindexing.Action;
import com.google.android.gms.appindexing.AppIndex;
import com.google.android.gms.appindexing.Thing;
import com.google.android.gms.common.api.GoogleApiClient;
import com.baidu.trace.Trace;
import com.baidu.trace.LBSTraceClient;
import com.baidu.trace.model.OnCustomAttributeListener;
import com.baidu.trace.model.OnTraceListener;
import com.baidu.trace.api.track.OnTrackListener;
import com.baidu.trace.api.fence.OnFenceListener;
import com.baidu.trace.api.entity.OnEntityListener;
import com.baidu.trace.api.analysis.OnAnalysisListener;
import com.baidu.trace.api.bos.OnBosListener;


import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    public static final int PLAYBACK_OVER = 1;
    private AlertDialog dialog = null;
    private GeoCoder geoCoder;
    private boolean isTracking = false,isNeedObjectStorage;
    private int currentTrackLineID,tag;
    private String currentAddr,entityName, routeLength;
    private Double currentLng, currentLat,distance;
    public LocationClient mLocationClient;
    private MapView mapView;
    private BaiduMap baiduMap;
    private boolean isFirstLocation = true;
    private DatabaseAdapter dbAdapter;
    private Button myLocation, start, stop, replay;
    //用于存储两个点的经纬度，再划线。
    private static ArrayList<LatLng> list = new ArrayList<>();
//    保存两点两点距离和的集合
    private static ArrayList<Double> length=new ArrayList<>();
    private LBSTraceClient mTraceClient;
    private Trace mTrace;
    private long serviceId,startTime,endTime;
//    private MyTraceListener mTraceListener;
//    private MyTrackListener mTrackListener;
    private MyLocationClickListener myLocationClickListener;
    private static final  double EARTH_RADIUS = 6378137;//赤道半径(单位m)




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //        初始化且一定要在setContentView()前
        SDKInitializer.initialize(getApplicationContext());
        setContentView(R.layout.activity_main);
        mapView = (MapView) findViewById(R.id.bmapView);
        myLocation = (Button) findViewById(R.id.bt_mylocation);
        myLocation.setOnClickListener(this);
        start = (Button) findViewById(R.id.bt_start);
        start.setOnClickListener(this);
        stop = (Button) findViewById(R.id.bt_stop);
        stop.setOnClickListener(this);
        replay = (Button) findViewById(R.id.bt_replay);
        replay.setOnClickListener(this);
        initView();//初始化地图，显示我的位置
        dbAdapter = new DatabaseAdapter(this);
    }

    //   初始化工作
    private void initView() {

        baiduMap = mapView.getMap();
//        设置地图的显示模式
      /*  baiduMap.setMapType(BaiduMap.MAP_TYPE_NORMAL);//普通地图
        baiduMap.setMapType(BaiduMap.MAP_TYPE_SATELLITE);//卫星地图
        baiduMap.setMapType(BaiduMap.MAP_TYPE_NONE);//空白地图  （节省流量）*/
//        允许我的位置出现在map上的设置
        baiduMap.setMyLocationEnabled(true);
//        实时交通图
//        baiduMap.setTrafficEnabled(true);
        askPermisssion();
        joinWhiteList();
//        定位百度地图SDK核心类
        myLocationClickListener = new MyLocationClickListener();
//        轨迹追踪监听
//        mTraceListener=new MyTraceListener();
//        mTrackListener=new MyTrackListener();
        mLocationClient = new LocationClient(getApplicationContext());
        mLocationClient.registerLocationListener(myLocationClickListener);//注册监听函数
//        初始化定位

        LocationClientOption option = new LocationClientOption();
        option.setLocationMode(LocationClientOption.LocationMode.Hight_Accuracy);// 设置定位模式
        option.setCoorType("bd09ll");// 返回的定位结果是百度经纬度,默认值gcj02
        option.setScanSpan(5000);// 设置发起定位请求的间隔时间为5000ms
        option.setIsNeedAddress(true);// 返回的定位结果包含地址信息
        option.setNeedDeviceDirect(true);// 返回的定位结果包含手机机头的方向
        mLocationClient.setLocOption(option);
        mLocationClient.start();// 启动SDK定位
        mLocationClient.requestLocation();// 发起定位请求

        //用于转换地理编码的监听器
        geoCoder = GeoCoder.newInstance();
        geoCoder.setOnGetGeoCodeResultListener(new OnGetGeoCoderResultListener() {
            @Override
            public void onGetReverseGeoCodeResult(ReverseGeoCodeResult result) {
                if (result == null
                        || result.error != SearchResult.ERRORNO.NO_ERROR) {
                    // 没有检索到结果
                } else {
                    // 获取地理编码结果
                    // System.out.println(result.getAddress());
                    currentAddr = result.getAddress();
                    //更新线路的结束位置
                    dbAdapter.updateEndLoc(currentAddr, currentTrackLineID);
                }
            }

            @Override
            public void onGetGeoCodeResult(GeoCodeResult arg0) {

            }
        });

/*//初始化轨迹服务
        tag=1;
        // 轨迹服务ID
        serviceId =142746;
// 设备标识
        entityName = "myTrace";
// 是否需要对象存储服务，默认为：false，关闭对象存储服务。注：鹰眼 Android SDK v3.0以上版本支持随轨迹上传图像等对象数据，若需使用此功能，该参数需设为 true，且需导入bos-android-sdk-1.0.2.jar。
        isNeedObjectStorage = false;
// 初始化轨迹服务
         mTrace = new Trace(serviceId, entityName, isNeedObjectStorage);
// 初始化轨迹服务客户端
         mTraceClient = new LBSTraceClient(getApplicationContext());
//        设置轨迹追踪监听器
        mTraceClient.setOnTraceListener(mTraceListener);*/

    }


    /*public void navigateTo(BDLocation location) {
        if (isFirstLocation && location != null) {
            isFirstLocation = false;
            currentAddr = location.getAddrStr();
            currentLat = location.getLatitude();
            currentLng = location.getLongitude();
            //绘制图层   标注覆盖物
            //构建Marker图标
*//*            BitmapDescriptor bitmap = BitmapDescriptorFactory.fromResource(R.mipmap.location);
            //构建MarkerOption，用于在地图上添加Marker
            OverlayOptions option = new MarkerOptions().position(ll).icon(bitmap);*//*
            //  移动图标到自己的位置
            MyLocationData.Builder locationBuilder = new MyLocationData.Builder();
            locationBuilder.latitude(location.getLatitude());
            locationBuilder.longitude(location.getLongitude());
            locationBuilder.accuracy(location.getRadius());//设置精度半径
            locationBuilder.direction(location.getDirection());//设置方向
            locationBuilder.speed(location.getSpeed());//设置速度
            MyLocationData locationData = locationBuilder.build();
//            把我的我I位置信息设置到地图上
            baiduMap.setMyLocationData(locationData);
            LatLng ll = new LatLng(location.getLatitude(), location.getLongitude());
            baiduMap.setMyLocationConfigeration(new MyLocationConfiguration(MyLocationConfiguration.LocationMode.FOLLOWING, true, null));
//           配置我的位置为地图的中心点
            MapStatusUpdate update = MapStatusUpdateFactory.newLatLng(ll);
            baiduMap.animateMapStatus(update);
//            缩放级别3到20
            update = MapStatusUpdateFactory.zoomTo(16f);
            baiduMap.animateMapStatus(update);
            //在地图上添加Marker，并显示
//            baiduMap.addOverlay(option);
//            isFirstLocation=false;
        }
    }
*/
/*
    //    跟踪位置变化
    private void initLocation() {
        LocationClientOption option = new LocationClientOption();
//        设置的导航模式有三种：Device_Sensors、Hight_Accuracy、Battery_Saving
        option.setLocationMode(LocationClientOption.LocationMode.Battery_Saving);
//        用于转换地理编码的监听器

        option.setIsNeedAddress(true);
//        定义请求时间
        int span = 5000;
        option.setScanSpan(span);
//        是否设置手机方向
        option.setNeedDeviceDirect(true);
//        使用GPS
//        option.setOpenGps(true);
//        可选，默认false，设置是否当GPS有效时按照1S/1次频率输出GPS结果
//        option.setLocationNotify(true);
//        设置百度经纬度坐标系格式，可以不设置
        option.setCoorType("bd09ll");
        //可选，默认false，设置是否需要位置语义化结果，可以在BDLocation.getLocationDescribe里得到，结果类似于“在北京天安门附近”
//        option.setIsNeedLocationDescribe(true);
//可选，默认false，设置是否需要POI结果，可以在BDLocation.getPoiList里得到
//        option.setIsNeedLocationPoiList(true);
        //可选，默认false，设置是否收集CRASH信息，默认收集
//        option.SetIgnoreCacheException(false);
        //可选，默认false，设置是否需要过滤GPS仿真结果，默认需要
//        option.setEnableSimulateGps(false);
        mLocationClient.setLocOption(option);

//设置定位回调周期
        // 定位周期(单位:秒)
        int gatherInterval = 5;
// 打包回传周期(单位:秒)
        int packInterval = 10;
// 设置定位和打包周期
        mTraceClient.setInterval(gatherInterval, packInterval);
    }
*/


    //    运行时权限的判断及请求
    public void askPermisssion() {
        List<String> permissionList = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionList.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            permissionList.add(Manifest.permission.READ_PHONE_STATE);
        }
        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            permissionList.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        if (!permissionList.isEmpty()) {
            String[] permissions = permissionList.toArray(new String[permissionList.size()]);
            ActivityCompat.requestPermissions(MainActivity.this, permissions, 1);
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case 1:
                if (grantResults.length > 0) {
                    for (int result : grantResults) {
                        if (result != PackageManager.PERMISSION_GRANTED) {
                            Toast.makeText(this, "必须同意所有权限才能使用本程序", Toast.LENGTH_LONG).show();
                            finish();
                            return;
                        }
                    }

                } else {
                    Toast.makeText(this, "发生未知错误", Toast.LENGTH_LONG).show();
                    finish();
                }
                break;
            default:
        }
    }


    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mLocationClient.stop();
        mapView.onDestroy();
        baiduMap.setMyLocationEnabled(false);
//        mTraceClient.stopTrace(mTrace, mTraceListener);
    }

    //四个点击事件
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.bt_mylocation:
                setMyLocation();
                break;
            case R.id.bt_start:
                startTrack();
                break;
            case R.id.bt_stop:
                stopTrack();
                break;
            case R.id.bt_replay:
                replayTrack();
                break;
        }

    }


    public class MyLocationClickListener implements BDLocationListener {
        @Override
        public void onReceiveLocation(BDLocation location) {
            if (location != null && isFirstLocation) {
                isFirstLocation = false;
                currentLat = location.getLatitude();//当前的纬度
                currentLng = location.getLongitude();//当前的经度
                currentAddr = location.getAddrStr();//当前位置的地址
                // System.out.println("crrentAddr=" + currentAddr);
                //构造我的当前位置信息
                MyLocationData.Builder builder = new MyLocationData.Builder();
                builder.latitude(location.getLatitude());// 设置纬度
                builder.longitude(location.getLongitude());// 设置经度
                builder.accuracy(location.getRadius());// 设置精度（半径）
                builder.direction(location.getDirection());// 设置方向
                builder.speed(location.getSpeed());// 设置速度
                MyLocationData locationData = builder.build();

                //把我的位置信息设置到地图上
                baiduMap.setMyLocationData(locationData);
                //配置我的位置
                LatLng latlng = new LatLng(currentLat, currentLng);
                baiduMap.setMyLocationConfigeration(new MyLocationConfiguration(
                        com.baidu.mapapi.map.MyLocationConfiguration.LocationMode.FOLLOWING,
                        true, null));
                // 设置我的位置为地图的中心点(缩放级别为 3-20)
                baiduMap.animateMapStatus(MapStatusUpdateFactory.newLatLngZoom(
                        latlng, 16));
            }
            StringBuilder currentPosition = new StringBuilder();
            currentPosition.append("时间：").append(location.getTime()).append("\n");
            currentPosition.append("LocType码：").append(location.getLocType()).append("\n");
            currentPosition.append("Raadius：").append(location.getRadius()).append("\n");
            currentPosition.append("纬度：").append(location.getLatitude()).append("\n");
            currentPosition.append("经度：").append(location.getLongitude()).append("\n");
            currentPosition.append("国家：").append(location.getCountry()).append("\n");
            currentPosition.append("省：").append(location.getProvince()).append("\n");
            currentPosition.append("市：").append(location.getCity()).append("\n");
            currentPosition.append("区：").append(location.getDistrict()).append("\n");
            currentPosition.append("街道：").append(location.getStreet()).append("\n");
            currentPosition.append("定位方式：");
            if (location.getLocType() == BDLocation.TypeGpsLocation) {
                currentPosition.append("GPS");
            }
            if (location.getLocType() == BDLocation.TypeNetWorkLocation) {
                currentPosition.append("网络");
            }
            Log.d("CC", "你的位置：" + currentPosition.toString());
        }

        @Override
        public void onConnectHotSpotMessage(String s, int i) {
        }
    }


/*
//    轨迹追踪监听
public class MyTraceListener implements OnTraceListener{

    @Override
    public void onStartTraceCallback(int status, String message) {}
    // 停止服务回调
    @Override
    public void onStopTraceCallback(int status, String message) {}
    // 开启采集回调
    @Override
    public void onStartGatherCallback(int status, String message) {}
    // 停止采集回调
    @Override
    public void onStopGatherCallback(int status, String message) {}
    // 推送回调
    @Override
    public void onPushCallback(byte messageNo, PushMessage message) {}

    @Override
    public void onBindServiceCallback(int i, String s) {}

};
    public class MyTrackListener extends OnTrackListener {
        @Override
        public void onHistoryTrackCallback(HistoryTrackResponse historyTrackResponse) {

        }

        @Override
        public void onDistanceCallback(DistanceResponse distanceResponse) {
            super.onDistanceCallback(distanceResponse);
        }
    }
*/







    public void setMyLocation() {
        Toast.makeText(MainActivity.this, "正在定位中...", Toast.LENGTH_SHORT)
                .show();
        isFirstLocation = true;
        baiduMap.clear();//清除地图上自定义的图层
        baiduMap.setMyLocationEnabled(true);
        mLocationClient.requestLocation();// 发起定位请求
    }

    public void startTrack() {
/*//        1. 开启鹰眼服务，启动鹰眼 service
// 开启服务
        mTraceClient.startTrace(mTrace, mTraceListener);
//        2. 开启轨迹采集，启动轨迹追踪。至此，正式开启轨迹追踪。
// 开启采集
        mTraceClient.startGather(mTraceListener);
        startTime=System.currentTimeMillis();
        Toast.makeText(this, "轨迹追踪正在开启……", Toast.LENGTH_SHORT).show();*/

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("线路跟踪");
        builder.setCancelable(true);
        final View view = getLayoutInflater().inflate(
                R.layout.add_track_line_dialog, null);
        builder.setView(view);
        builder.setPositiveButton("添加", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                EditText et_track_name = (EditText) view
                        .findViewById(R.id.editText_track_name);
                String trackName = et_track_name.getText().toString();
                System.out.println(trackName);
                createTrack(trackName);//创建线路跟踪
                Toast.makeText(MainActivity.this, "跟踪开始...", Toast.LENGTH_SHORT)
                        .show();
            }
        });
        builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        builder.show();
    }

    public void stopTrack() {
        if(isTracking) {
            double sum = 0;
            isTracking = false;// 结束线程
//        TODO
            for (int i = 0; i < length.size(); i++) {
                double j = length.get(i);
                sum += j;
                routeLength = new java.text.DecimalFormat("#.00").format(sum);
            }
            Log.d("DISTANCE", "你此次行进了" + routeLength + "m");
            Toast.makeText(MainActivity.this, "跟踪结束...你行进了" + routeLength + "m", Toast.LENGTH_LONG).show();
            //转换地理编码,把最后的一个经纬度转换成地址
            geoCoder.reverseGeoCode(new ReverseGeoCodeOption().location(new LatLng(
                    currentLat, currentLng)));
/*        // 停止服务  把服务停止了再需要追踪的时候需要重新启动追踪服务
//        mTraceClient.stopTrace(mTrace, mTraceListener);
        // 停止采集
        mTraceClient.stopGather(mTraceListener);
        endTime=System.currentTimeMillis();
        Toast.makeText(this, "轨迹追踪正在关闭……", Toast.LENGTH_SHORT).show();*/
        }else{
            Toast.makeText(this, "你还没有开始记录轨迹，请选择开始。", Toast.LENGTH_SHORT).show();
        }
    }

    public void replayTrack() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(false);
        builder.setTitle("跟踪路线列表");
        View view = getLayoutInflater().inflate(
                R.layout.track_line_playback_dialog, null);
        ListView playbackListView = (ListView) view
                .findViewById(R.id.dialog_listView);

        ArrayList<HashMap<String, String>> data = new ArrayList<HashMap<String, String>>();
        ArrayList<Track> tracks = dbAdapter.getTracks();//得到所有Track
        HashMap<String, String> map = null;
        Track t = null;
        for (int i = 0; i < tracks.size(); i++) {
            map = new HashMap<String, String>();
            t = tracks.get(i);
            map.put("id", String.valueOf(t.getId()));
            map.put("trackName_createDate",
                    "路线："+t.getTrack_name() + "   时间：" + t.getCreate_date());
            map.put("startEndLoc",
                    "起点： " + t.getStart_loc() +"   终点：" + t.getEnd_loc());
            data.add(map);//data里存放查到的所有track的信息 即线路创建时间、名字、开始结束位置。
        }
        SimpleAdapter adapter = new SimpleAdapter(this, data,
                R.layout.playback_item, new String[] { "id",
                "trackName_createDate", "startEndLoc" }, new int[] {
                R.id.textview_id, R.id.textview_trackName_createTime,
                R.id.textview_startEndLoc });
        playbackListView.setAdapter(adapter);
        playbackListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view,
                                    int position, long id) {
                TextView tv_id = (TextView) view.findViewById(R.id.textview_id);
                int _id = Integer.parseInt(tv_id.getText().toString());
                baiduMap.clear();
                new Thread(new TrackPlaybackThread(_id)).start();
                dialog.dismiss();
            }
        });
        builder.setView(view);
        builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        dialog = builder.create();
        dialog.show();
/*        // 创建历史轨迹请求实例
        HistoryTrackRequest historyTrackRequest = new HistoryTrackRequest(tag, serviceId, entityName);
        historyTrackRequest.setStartTime(startTime);
        historyTrackRequest.setEndTime(endTime);
        // 设置需要纠偏
        historyTrackRequest.setProcessed(true);
// 创建纠偏选项实例
        ProcessOption processOption = new ProcessOption();
// 设置需要去噪
        processOption.setNeedDenoise(true);
// 设置需要抽稀
        processOption.setNeedVacuate(true);
// 设置需要绑路
        processOption.setNeedMapMatch(true);
// 设置精度过滤值(定位精度大于100米的过滤掉)
        processOption.setRadiusThreshold(100);
// 设置交通方式为不行
        processOption.setTransportMode(TransportMode.walking);
// 设置纠偏选项
        historyTrackRequest.setProcessOption(processOption);
// 设置里程填充方式为步行
        historyTrackRequest.setSupplementMode(SupplementMode.walking);
        mTraceClient.queryHistoryTrack(historyTrackRequest, mTrackListener);*/
    }
/*    public void calcDistance(){
        // 创建里程查询请求实例
        DistanceRequest distanceRequest = new DistanceRequest(tag, serviceId, entityName);
        distanceRequest.setStartTime(startTime);
        distanceRequest.setEndTime(endTime);
        // 设置需要纠偏
        distanceRequest.setProcessed(true);
// 创建纠偏选项实例
        ProcessOption processOption = new ProcessOption();
// 设置需要去噪
        processOption.setNeedDenoise(true);
// 设置需要绑路
        processOption.setNeedMapMatch(true);
// 设置交通方式为驾车
        processOption.setTransportMode(TransportMode.walking);
// 设置纠偏选项
        distanceRequest.setProcessOption(processOption);
// 设置里程填充方式为驾车
        distanceRequest.setSupplementMode(SupplementMode.walking);
// 查询里程
        mTraceClient.queryDistance(distanceRequest, mTrackListener);

    }*/
//    申请加入白名单，保证鹰眼服务的存活
    public void joinWhiteList(){


        // 在Android 6.0及以上系统，若定制手机使用到doze模式，请求将应用添加到白名单。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String packageName = this.getPackageName();
            PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
            boolean isIgnoring = powerManager.isIgnoringBatteryOptimizations(packageName);
            if (!isIgnoring) {
                Intent intent = new Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + packageName));
                try {
                    startActivity(intent);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }

    }



    private void createTrack(String trackName) {
        Track track = new Track();
        track.setTrack_name(trackName);
        track.setCreate_date(DataUtils.toDate(new Date()));
        track.setStart_loc(currentAddr);
        currentTrackLineID = dbAdapter.addTrack(track);
        dbAdapter.addTrackDetail(currentTrackLineID, currentLat, currentLng);
        baiduMap.clear();
        addOverlay();
        list.add(new LatLng(currentLat, currentLng));
        isTracking = true;//线程模拟的标记
        System.out.println(list);
        new Thread(new TrackThread()).start();
    }

    //在地图上添加图层（线路的每一个点）
    private void addOverlay() {
        // ----------添加一个标注覆盖物在当前位置--------------
        // 构建Marker图标
        baiduMap.setMyLocationEnabled(false);// 关闭定位图层
        BitmapDescriptor bitmap = BitmapDescriptorFactory
                .fromResource(R.drawable.b_poi);
        // 构建MarkerOption，用于在地图上添加Marker
        LatLng latlng = new LatLng(currentLat, currentLng);
        OverlayOptions option = new MarkerOptions().position(latlng).icon(
                bitmap);
        // 在地图上添加Marker，并显示
        baiduMap.addOverlay(option);
        //把当前添加的位置作为地图的中心点
        baiduMap.animateMapStatus(MapStatusUpdateFactory.newLatLng(latlng));
    }

//    模拟跟踪的线程
class TrackThread implements Runnable {

    @Override
    public void run() {
        while (isTracking) {
//            getLocation();//模拟更新位置的方法
            dbAdapter.addTrackDetail(currentTrackLineID, currentLat,
                    currentLng);
            addOverlay();
            list.add(new LatLng(currentLat, currentLng));
//            得到list中的第一个LatLng信息
            LatLng latLng1=list.get(0);
//            得到list中的第二个LatLng信息
            LatLng latLng2=list.get(1);
//            TODO 算距离任重道远
//            计算两点的距离
            distance=GetDistance(latLng1.longitude,latLng1.latitude,latLng2.longitude,latLng2.latitude);
            length.add(distance);
            Log.d("DISTANCE","你走了"+distance+"m");
            drawLine();
            System.out.println("drawLine");
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
    //在两个点之间画线
    private void drawLine() {
        OverlayOptions lineOptions = new PolylineOptions().points(list).color(
                0xFFFF0000);
        baiduMap.addOverlay(lineOptions);
        list.remove(0);
    }

    /**
     * 模拟位置
     */
    private void getLocation() {
        currentLat = currentLat + Math.random() / 1000;
        currentLng = currentLng + Math.random() / 1000;
    }


    /**
     * 跟踪回放的线程
     */
    class TrackPlaybackThread implements Runnable{
        private int id;
        public TrackPlaybackThread(int id){
            this.id = id;
        }
        @Override
        public void run() {
            ArrayList<TrackDetail> trackDetails = dbAdapter.getTrackDetails(id);
            TrackDetail td = null;
            list.clear();
//            找出第一个点，将mark添加到地图
            currentLat = trackDetails.get(0).getLat();
            currentLng = trackDetails.get(0).getLng();
            list.add(new LatLng(currentLat, currentLng));
            addOverlay();
//            找出所有其他点，将mark添加到地图，在每个两点之间画出轨迹
            for (int i = 1; i < trackDetails.size(); i++) {
                td = trackDetails.get(i);
                currentLat = td.getLat();
                currentLng = td.getLng();
                list.add(new LatLng(currentLat, currentLng));
                addOverlay();
                drawLine();
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            handler.sendEmptyMessage(PLAYBACK_OVER);
        }
    }
    private Handler handler = new Handler(){
        public void handleMessage(android.os.Message msg) {
            switch (msg.what) {
                case PLAYBACK_OVER:
//                    TODO  你解决好了吗？
                    Toast.makeText(MainActivity.this, "回放结束……", Toast.LENGTH_SHORT).show();
                    break;

                default:
                    break;
            }
        };
    };


    /**
     * 转化为弧度(rad)
     * */
    private static double rad(double d)
    {
        return d * Math.PI / 180.0;
    }
    /**
     * 基于googleMap中的算法得到两经纬度之间的距离,计算精度与谷歌地图的距离精度差不多，相差范围在0.2米以下
     * @param lon1 第一点的精度
     * @param lat1 第一点的纬度
     * @param lon2 第二点的精度
     * @param lat2 第二点的纬度
     * @return 返回的距离，单位km
     * */
    public static double GetDistance(double lon1,double lat1,double lon2, double lat2)
    {
        double radLat1 = rad(lat1);
        double radLat2 = rad(lat2);
        double a = radLat1 - radLat2;
        double b = rad(lon1) - rad(lon2);
        double s = 2 * Math.asin(Math.sqrt(Math.pow(Math.sin(a/2),2)+Math.cos(radLat1)*Math.cos(radLat2)*Math.pow(Math.sin(b/2),2)));
        s = s * EARTH_RADIUS;
        //s = Math.round(s * 10000) / 10000;
        return s;
    }
}