package com.tatait.tataweibo.util.fragment;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.loopj.android.http.JsonHttpResponseHandler;
import com.loopj.android.http.RequestParams;
import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.sina.weibo.sdk.auth.Oauth2AccessToken;
import com.tatait.tataweibo.HomeActivity;
import com.tatait.tataweibo.MainActivity.UserSession;
import com.tatait.tataweibo.R;
import com.tatait.tataweibo.WriterWeiboActivity;
import com.tatait.tataweibo.adapter.HomeAdapters;
import com.tatait.tataweibo.bean.Constants;
import com.tatait.tataweibo.bean.ContentInfo;
import com.tatait.tataweibo.bean.UserInfo;
import com.tatait.tataweibo.util.AccessTokenKeeper;
import com.tatait.tataweibo.util.CommonUtil;
import com.tatait.tataweibo.util.DateUtils;
import com.tatait.tataweibo.util.HttpUtils;
import com.tatait.tataweibo.util.StringUtils;
import com.tatait.tataweibo.util.ToastUtil;
import com.tatait.tataweibo.util.Tools;
import com.tatait.tataweibo.util.file.FileService;
import com.tatait.tataweibo.util.show.CircularImage;
import com.tatait.tataweibo.util.show.XListView;
import com.tatait.tataweibo.util.show.XListView.IXListViewListener;

import org.apache.http.Header;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class MyFragment extends Fragment implements IXListViewListener {
    private static final String TAG = "MyFragment";
    @Bind(R.id.home_title_bar_user_photo)
    CircularImage homeTitleBarUserPhoto;
    @Bind(R.id.home_title_login_user)
    TextView homeTitleLoginUser;
    @Bind(R.id.menu_btn_right)
    Button menuBtnRight;
    @Bind(R.id.pg_progress)
    ProgressBar pg_progress;
    @Bind(R.id.load_progress)
    LinearLayout load_progress;
    @Bind(R.id.home_lv)
    XListView homeLv;

    //绑定点击事件
    @OnClick(R.id.menu_btn_right)
    public void click() {
        makePopupWindow();
    }

    @OnClick(R.id.home_title_bar_user_photo)
    public void openLeftLayout() {
        ((HomeActivity) getActivity()).openLeftLayout();
    }

    private ListView popuplist;
    private Date nowDate, startDate;
    // 保存需要显示的多条微博数据
    private static LinkedList<ContentInfo> contentList = null;
    private ArrayList<HashMap<String, String>> homeSetMenuList = new ArrayList<HashMap<String, String>>();
    private UserInfo user;
    // 微博认证
    private Oauth2AccessToken accessToken = null;
    private HomeAdapters adapater = null;
    private Tools tools;
    // 下拉刷新
    private FileService file;
    private PopupWindow pw = null;
    private int page = 1;
    private int screenWidth, screenHeight;
    private ImageLoader imageLoader;
    private DisplayImageOptions options;
    private static Activity mActivity;
    private static Context appContext;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View home_view = inflater.inflate(R.layout.home, null);
        ButterKnife.bind(this, home_view);//fragment : ButterKnife.bind(this, view);

        initValue();
        initView(inflater);
        requestNewData(Constants.GET_PUBLIC);

        return home_view;
    }

    public static void setContext(Activity activity,Context context){
        mActivity = activity;
        appContext = context;
    }

    //初始化值
    private void initValue() {
        imageLoader = ImageLoader.getInstance();
        options = new DisplayImageOptions.Builder()
//                .showImageOnLoading(R.drawable.bgpic)
//                .showImageOnFail(R.drawable.bgpic)
                .cacheInMemory(true)
                .cacheOnDisk(true)
                .bitmapConfig(Bitmap.Config.RGB_565)
                .build();
        user = UserSession.nowUser;
        //初始化标题栏：头像和登陆人
        homeTitleLoginUser.setText(user.getUser_name());
        imageLoader.displayImage(user.getUser_head(), homeTitleBarUserPhoto, options);
        file = new FileService(appContext);
        startDate = Calendar.getInstance().getTime();

        contentList = new LinkedList<ContentInfo>();
        adapater = new HomeAdapters((HomeActivity) mActivity, contentList);
        // 设置listview上的item点击事件处理
        homeLv.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view,
                                    int position, long id) {
                Object obj = view.getTag();
                if (obj != null) {
                    // 获得一条weibo数据的id（唯一标识）
                    String weiboId = obj.toString();
                    Toast.makeText(appContext, "选择了ID为：" + weiboId + "的记录。", Toast.LENGTH_LONG).show();
                    //跳转到一条具体的微博显示页面
//                     Intent intent = new Intent(getActivity(),ContentActivity.class);
//                     Bundle bundle = new Bundle();
//                     //参数的设置
//                     bundle.putString("weiboId", weiboId);
//                     //参数绑定
//                     intent.putExtras(bundle);
//                     startActivity(intent);
                }
            }
        });
        homeLv.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView paramAbsListView, int paramInt) {
                //当屏幕停止滚动时为0；当屏幕滚动且用户使用的触碰或手指还在屏幕上时为1；
                //由于用户的操作，屏幕产生惯性滑动时为2
//                if (paramInt == 1) {
//                    System.out.println("当屏幕滚动且用户使用的触碰或手指还在屏幕上时为1");
//                } else if (paramInt == 0) {
//                    System.out.println("当屏幕停止滚动时为0");
//                } else {
//                    System.out.println("由于用户的操作，屏幕产生惯性滑动时为2");
//                }
            }

            @Override
            public void onScroll(AbsListView paramAbsListView, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
//              //firstVisibleItem表示在现时屏幕第一个ListItem(部分显示的ListItem也算)
//              //  在整个ListView的位置（下标从0开始）
//              System.out.println("***firstParamInt:"+firstVisibleItem);
//              //  visibleItemCount表示在现时屏幕可以见到的ListItem(部分显示的ListItem也算)总数
//              System.out.println("***visibleItemCount:"+visibleItemCount);
//              //  totalItemCount表示ListView的ListItem总数
//              System.out.println("***totalItemCount:"+totalItemCount);
//                paramAbsListView.getLastVisiblePosition();
//              //表示在现时屏幕最后一个ListItem(最后ListItem要完全显示出来才算)在整个ListView的位置（下标从0开始）
//              System.out.println("****"+String.valueOf(paramAbsListView.getLastVisiblePosition()));

//                if (paramAbsListView.getLastVisiblePosition() == (paramAbsListView.getCount() - 1)) {
//                    final View bottomChildView = paramAbsListView.getChildAt(paramAbsListView.getLastVisiblePosition() - paramAbsListView.getFirstVisiblePosition());
//                    if(paramAbsListView.getHeight()>=bottomChildView.getBottom()){
//                        //处于底部
//                    }
//                }

                if (totalItemCount - paramAbsListView.getLastVisiblePosition() < 2) {
//                    System.out.println("****自动加载更多数据了****");
                    requestNewData(Constants.GET_PUBLIC_LOADMORE);
                    onLoad();
                }
            }
        });
        // 将adapter和listview关联
        homeLv.setAdapter(adapater);
        //设置下拉刷新
        homeLv.setXListViewListener(this);
        homeLv.setPullLoadEnable(true);
    }

    public void initView(LayoutInflater inflater) {
        screenWidth = CommonUtil.getScreenWidth(getActivity());
        screenHeight = CommonUtil.getScreenHeight(getActivity());
        View vPopupWindow = inflater.inflate(R.layout.home_set_menu, null);
        popuplist = (ListView) vPopupWindow.findViewById(R.id.popuplist);
        pw = new PopupWindow(vPopupWindow, screenWidth / 2,  RelativeLayout.LayoutParams.WRAP_CONTENT, true);
        //设置弹出框的List
        HashMap<String, String> map = new HashMap<String, String>();
        map.put("itemTitle", "刷新数据");
        homeSetMenuList.add(map);
        HashMap<String, String> map1 = new HashMap<String, String>();
        map1.put("itemTitle", "发表微博");
        homeSetMenuList.add(map1);
        HashMap<String, String> map2 = new HashMap<String, String>();
        map2.put("itemTitle", "注销登陆");
        homeSetMenuList.add(map2);
        HashMap<String, String> map4 = new HashMap<String, String>();
        map4.put("itemTitle", "退出程序");
        homeSetMenuList.add(map4);
        // 为弹出框设定自定义的布局
        pw.setContentView(vPopupWindow);
        pw.setFocusable(true);
        pw.setOutsideTouchable(true);
        pw.setBackgroundDrawable(getResources().getDrawable(R.color.white));
        // 生成SimpleAdapter适配器对象
        SimpleAdapter mySimpleAdapter = new SimpleAdapter(appContext, homeSetMenuList,
                R.layout.home_set_menu_list, new String[]{"itemTitle"}, new int[]{R.id.itemTitle});
        popuplist.setAdapter(mySimpleAdapter);
        // 添加点击事件
        popuplist.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> arg0, View arg1, int arg2,
                                    long arg3) {
                // 获得选中项的HashMap对象
                HashMap<String, String> map = (HashMap<String, String>) popuplist.getItemAtPosition(arg2);
                String title = map.get("itemTitle");
                pw.dismiss();
                if ("刷新数据".equals(title)) {
                    if (contentList.size() > 0) {
                        contentList.clear();
                    }
                    requestNewData(Constants.GET_PUBLIC);
                    Toast.makeText(appContext,R.string.dateFlash, Toast.LENGTH_LONG).show();
                } else if ("发表微博".equals(title)) {
                    startActivity(new Intent(mActivity, WriterWeiboActivity.class));
                } else if ("退出程序".equals(title)) {
                    mActivity.finish();
                    ((HomeActivity) mActivity).stopSer();
                    System.exit(0);
                } else if ("注销登陆".equals(title)) {
                    ((HomeActivity) mActivity).logoff();
                }
            }

        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        ButterKnife.unbind(this);
    }

    // IXListViewListener 需实现的方法
    @Override
    public void onRefresh() {
        requestNewData(Constants.GET_PUBLIC_REFLASH);
        onLoad();
    }

    // IXListViewListener 需实现的方法
    @Override
    public void onLoadMore() {
        requestNewData(Constants.GET_PUBLIC_LOADMORE);
        onLoad();
    }

    // 重写刷新时间
    private void onLoad() {
        homeLv.stopRefresh();
        homeLv.stopLoadMore();
        nowDate = Calendar.getInstance().getTime();
        String time = new DateUtils().twoDateDistanceForWeibo(startDate, nowDate);
        homeLv.setRefreshTime(time);
        startDate = nowDate;
    }

    public void makePopupWindow() {
        if (pw.isShowing()) {
            pw.dismiss();
        } else {
            int dialgoWidth = pw.getWidth();
            pw.showAsDropDown(menuBtnRight, (screenWidth - dialgoWidth) - 5, 2);
        }

    }

    public PopupWindow getPopupWindow() {
        return pw;
    }

    public void setPwShow() {
        if (pw != null) {
            int dialgoWidth = pw.getWidth();
            pw.showAsDropDown(menuBtnRight, (screenWidth - dialgoWidth) - 5, 2);
        }
    }

    private void requestNewData(final int type) {//type 默认0  重新加载1  加载更多2
        // 获得主要显示的数据
        String since_id = "0";
        String count = "20";
        if (type == 2) {
            ++page;
        }
        accessToken = AccessTokenKeeper.readAccessToken(appContext);
        if (type == 0) {
//            if (file != null && file.dataExpired()) {
            // 参数绑定
            RequestParams requestParams = new RequestParams();
            requestParams.put("source", Constants.APP_KEY);
            requestParams.put("access_token", accessToken.getToken());
            requestParams.put("count", count);
            requestParams.put("page", Integer.valueOf(page).toString());
            requestParams.put("base", "1");
            httpGetMethod(type, requestParams);
//            } else {
//                try {
//                    respose(type, file.readFile(FileService.PUBLICLINEINFO));
//                } catch (Exception e) {
//                    Log.e(TAG, e.toString());
//                }
//            }
        } else if (type == 1) {
            RequestParams requestParams = new RequestParams();
            since_id = contentList.getFirst().getId();
            requestParams.put("source", Constants.APP_KEY);
            requestParams.put("access_token", accessToken.getToken());
            requestParams.put("since_id", since_id);
            httpGetMethod(type, requestParams);
        } else if (type == 2) {
            RequestParams requestParams = new RequestParams();
            requestParams.put("source", Constants.APP_KEY);
            requestParams.put("access_token", accessToken.getToken());
            requestParams.put("count", count);
            requestParams.put("page", Integer.valueOf(page).toString());
            httpGetMethod(type, requestParams);
        }
    }

    private void httpGetMethod(final int type, RequestParams requestParams) {
        HttpUtils.getRequest(Constants.WEIBO_GET_STATUS_PUBLIC_TIMELINE, requestParams,
                new JsonHttpResponseHandler() {

                    @Override
                    public void onFinish() {
                        // 隐藏进度条
                        load_progress.setVisibility(View.GONE);
                        homeLv.setVisibility(View.VISIBLE);
                        super.onFinish();
                    }

                    @Override
                    public void onStart() {
                        super.onStart();
                    }

                    @Override
                    public void onFailure(int statusCode, Header[] headers,
                                          String responseString, Throwable throwable) {
                        ToastUtil.show(R.string.notice_get_userinfo_fail);
                        super.onFailure(statusCode, headers, responseString, throwable);
                    }

                    @Override
                    public void onSuccess(int statusCode, Header[] headers,
                                          JSONObject response) {
//                        if (file != null && type != 2) {//首次和重新加载的时候 重新保存文件
//                            if(type == 1) {//由于重新加载是加载最新数据，所以不需要清除原有文件
//                                file.clearData();
//                            }
//                            file.saveDate(response.toString());
//                        }
                        respose(type, response.toString());
                        super.onSuccess(statusCode, headers, response);
                    }

                });
    }

    public void respose(int flag, String jsonStr) {
        try {
            JSONObject json = new JSONObject(jsonStr);
            String statuses = json.getString("statuses");
            if (StringUtils.isEmpty2(statuses)) {
                ToastUtil.show("暂无更多数据.");
                return;
            }else{
                JSONArray data = new JSONArray(statuses);

//            for (int i = 0; i < data.length(); i++) {
//                JSONObject item = data.getJSONObject(i);
//                ContentInfo result = JSON.parseObject(StringUtils.getObjString(item), ContentInfo.class);
//                if (flag == 1) {
//                    contentList.addFirst(result);// 将单条微博数据设置到集合前方
//                } else {
//                    contentList.addLast(result);// 将单条微博数据设置到集合尾部
//                }
//            }
                // 因为返回的是JSONArray表示包含了多条weibo数据，所以进行循环解析
                for (int i = 0; i < data.length(); i++) {
                    // 获得单条微博数据
                    JSONObject d = data.getJSONObject(i);
                    if (d != null) {
                        // 创建一个对象存储每条微博数据
                        ContentInfo contentInfo = new ContentInfo();
                        // 获得用户数据
                        JSONObject user = d.getJSONObject("user");
                        if (d.has("retweeted_status")) {
                            JSONObject r = d.getJSONObject("retweeted_status");
                        }
                        String id = d.getString("id");// 获得一条wiebo id
                        String userId = user.getString("id");// 获得发weibo 用户id
                        String userName = user.getString("screen_name");// 获得发weibo 用户的名称
                        String userIcon = user.getString("profile_image_url");// 获得发weibo 用户的头像url链接
                        String time = d.getString("created_at");// 获得发weibo的时间
                        String text = d.getString("text");// 获得weibo内容
                        Boolean follow_me = user.getBoolean("following");
                        Boolean haveImg = false;// 判断微博存在带图片信息
                        if (d.has("thumbnail_pic")) {
                            haveImg = true;
                            String thumbnail_pic = d.getString("thumbnail_pic");// 获得缩略图url链接
                            contentInfo.setImage_context(thumbnail_pic);
                        }

                        Date startDate = new Date(time);// 通过字符串构造发微博的时间
                        Date nowDate = Calendar.getInstance().getTime();// 获得当前时间
                        time = new DateUtils().twoDateDistanceForWeibo(startDate, nowDate);// 比较发表微博时间和当前时间之间距离时常
                        String content_source = d.getString("source");
                        if (contentList == null) {
                            contentList = new LinkedList<ContentInfo>();// 创建存储每条微博的集合
                        }
                        // 数据设置
                        contentInfo.setId(id);
                        contentInfo.setBiaotai_num(d.getString("attitudes_count"));//表态数
                        contentInfo.setShare_num(d.getString("reposts_count"));//转发数
                        contentInfo.setPinglun_num(d.getString("comments_count"));//评论数
                        contentInfo.setFollow_me(follow_me);
                        contentInfo.setUserId(userId);
                        contentInfo.setUserName(userName);
                        contentInfo.setTime(time);
                        contentInfo.setText(text);
                        contentInfo.setHaveImage(haveImg);
                        contentInfo.setUserIcon(userIcon);
                        contentInfo.setContent_source(content_source);

                        if (flag == 1) {
                            contentList.addFirst(contentInfo);// 将单条微博数据设置到集合前方
                        } else {
                            contentList.addLast(contentInfo);// 将单条微博数据设置到集合尾部
                        }
                    }
                }
                adapater.notifyDataSetChanged();
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}
