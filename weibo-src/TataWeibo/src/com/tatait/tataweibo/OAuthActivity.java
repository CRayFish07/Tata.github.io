package com.tatait.tataweibo;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.loopj.android.http.JsonHttpResponseHandler;
import com.loopj.android.http.RequestParams;
import com.sina.weibo.sdk.auth.AuthInfo;
import com.sina.weibo.sdk.auth.Oauth2AccessToken;
import com.sina.weibo.sdk.auth.WeiboAuthListener;
import com.sina.weibo.sdk.auth.sso.SsoHandler;
import com.sina.weibo.sdk.exception.WeiboException;
import com.sina.weibo.sdk.openapi.models.User;
import com.tatait.tataweibo.bean.Constants;
import com.tatait.tataweibo.bean.UserInfo;
import com.tatait.tataweibo.dao.UserDao;
import com.tatait.tataweibo.share_auth.AuthActivity;
import com.tatait.tataweibo.util.AccessTokenKeeper;
import com.tatait.tataweibo.util.Global;
import com.tatait.tataweibo.util.HttpUtils;
import com.tatait.tataweibo.util.ProgressDialogBar;
import com.tatait.tataweibo.util.SMSUtils;
import com.tatait.tataweibo.util.SharedPreferencesUtils;
import com.tatait.tataweibo.util.ToastUtil;
import com.umeng.socialize.UMAuthListener;
import com.umeng.socialize.UMShareAPI;
import com.umeng.socialize.bean.SHARE_MEDIA;

import org.apache.http.Header;
import org.json.JSONObject;

import java.util.Map;

/**
 * 授权页面
 *
 * @author WSXL
 */
public class OAuthActivity extends Activity {
    private long exitTime = 0;
    private static final String TAG = "OAuthActivity";
    /**
     * OAuth2.0认证--SSO授权
     */
    // 封装了 "access_token"，"expires_in"，"refresh_token"，并提供了他们的管理功能
    private Oauth2AccessToken mAccessToken;
    // 注意：SsoHandler 仅当 SDK 支持 SSO 时有效
    private SsoHandler mSsoHandler;
    private AuthInfo mAuthInfo;
    private Context mContext;

    private UMShareAPI mShareAPI = null;
    private boolean ready = false;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "----------in method:onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.load);
        /** init auth api**/
        mShareAPI = UMShareAPI.get(this);
        /**
         * 模拟器区别初始化:获取当前已保存过的 Token
         */
//		mAccessToken = AccessTokenKeeper.readAccessToken(this);
        mAuthInfo = new AuthInfo(this, Constants.APP_KEY, Constants.REDIRECT_URL, Constants.SCOPE);
        mSsoHandler = new SsoHandler(OAuthActivity.this, mAuthInfo);
        mContext = this;
        /**
         * 授权按钮的动作
         */
        Button startBtn = (Button) findViewById(R.id.xinlangButton);
        LinearLayout weixin = (LinearLayout) findViewById(R.id.weixin);
        LinearLayout qq = (LinearLayout) findViewById(R.id.QQ);
        LinearLayout nologin = (LinearLayout) findViewById(R.id.nologin);
        TextView load_gui_sms = (TextView) findViewById(R.id.load_gui_sms);
        TextView load_zidingyi_sms = (TextView) findViewById(R.id.load_zidingyi_sms);
        //新浪微博授权
        startBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                /**
                 * 执行SSO授权
                 */
                mSsoHandler.authorize(new AuthListener());
//				getUserInfo(mAccessToken);
            }

        });
        //微信登录
        weixin.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                /**begin invoke umeng api**/
                mShareAPI.doOauthVerify(OAuthActivity.this, SHARE_MEDIA.WEIXIN, umAuthListener);
            }
        });
        //QQ登录
        qq.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                /**begin invoke umeng api**/
                mShareAPI.doOauthVerify(OAuthActivity.this, SHARE_MEDIA.QQ, umAuthListener);
            }
        });
        //游客登录--》没有个人中心
        nologin.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                SharedPreferencesUtils.setParam(OAuthActivity.this, Global.LOGIN_TYPE, Global.LOGIN_TYPE_YK);
                JumpToNextStep("LoginOther");
            }
        });
        load_gui_sms.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                ready = true;
                Global.handlerForSMSLogin = handlerForSMSLogin;
                SMSUtils.init(OAuthActivity.this);
            }
        });
        load_zidingyi_sms.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                Global.handlerForSMSLogin = handlerForSMSLogin;
                Intent intent = new Intent(OAuthActivity.this, SMSLoginActivity.class);
                startActivity(intent);
                finish();
            }
        });
    }
    /**
     * 子线程回调
     */
    public Handler handlerForSMSLogin = new Handler() {
        @Override
        public void handleMessage(final Message msg) {
            super.handleMessage(msg);
            Bundle bundle = msg.getData();
            switch (msg.what) {
                case 666://GUI短信验证回调
                    startActivity(new Intent(OAuthActivity.this, TabMainActivity.class));
//                    finish();
                    break;
            }
        }
    };
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        mShareAPI.onActivityResult(requestCode, resultCode, data);
        if (mSsoHandler != null) {
            mSsoHandler.authorizeCallBack(requestCode, resultCode, data);
        }
    }

    /**
     * 微博认证授权回调类。 1. SSO 授权时，需要在 {@link #onActivityResult} 中调用
     * {@link SsoHandler#authorizeCallBack} 后， 该回调才会被执行。 2. 非 SSO
     * 授权时，当授权结束后，该回调就会被执行。 当授权成功后，请保存该 access_token、expires_in、uid 等信息到
     * SharedPreferences 中。
     */
    class AuthListener implements WeiboAuthListener {
        // 授权完成时
        @Override
        public void onComplete(Bundle values) {
            // 从 Bundle 中解析 Token
            Log.i(TAG, "SUCCESS");
            SharedPreferencesUtils.setParam(OAuthActivity.this, Global.LOGIN_TYPE, Global.LOGIN_TYPE_WB);
            mAccessToken = Oauth2AccessToken.parseAccessToken(values);
            if (mAccessToken.isSessionValid()) {
                // 保存 Token 到 SharedPreferences
                AccessTokenKeeper.writeAccessToken(OAuthActivity.this,
                        mAccessToken);
                Toast.makeText(OAuthActivity.this,R.string.weibosdk_demo_toast_auth_success,Toast.LENGTH_SHORT).show();
                getUserInfo(mAccessToken);
            } else {
                // 以下几种情况，您会收到 Code：
                // 1. 当您未在平台上注册的应用程序的包名与签名时；
                // 2. 当您注册的应用程序包名与签名不正确时；
                // 3. 当您在平台上注册的包名和签名与您当前测试的应用的包名和签名不匹配时。
                String code = values.getString("code");
                String message = getString(R.string.weibosdk_demo_toast_auth_failed);
                if (!TextUtils.isEmpty(code)) {
                    message = message + "\nObtained the code: " + code;
                }
                Toast.makeText(OAuthActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        }

        // 授权被取消时
        @Override
        public void onCancel() {
            Toast.makeText(OAuthActivity.this,R.string.weibosdk_demo_toast_auth_canceled,Toast.LENGTH_SHORT).show();
            SharedPreferencesUtils.setParam(OAuthActivity.this, Global.LOGIN_TYPE, Global.LOGIN_TYPE_NULL);
        }

        // 授权出错时
        @Override
        public void onWeiboException(WeiboException arg0) {
            Toast.makeText(OAuthActivity.this,"Auth exception : " + arg0.getMessage(), Toast.LENGTH_SHORT).show();
            SharedPreferencesUtils.setParam(OAuthActivity.this, Global.LOGIN_TYPE, Global.LOGIN_TYPE_NULL);
        }
    }

    /**
     * 获取用户信息
     *
     * @param mAccessToken
     */
    public void getUserInfo(Oauth2AccessToken mAccessToken) {
        // source false string 采用OAuth授权方式不需要此参数，其他授权方式为必填参数，数值为应用的AppKey。
        // access_token false string 采用OAuth授权方式为必填参数，其他授权方式不需要此参数，OAuth授权后获得。
        // uid false int64 需要查询的用户ID。
        // screen_name false string 需要查询的用户昵称。
        RequestParams requestParams = new RequestParams();
        requestParams.put("source", Constants.APP_KEY);
        requestParams.put("uid", mAccessToken.getUid());
        requestParams.put("access_token", mAccessToken.getToken());
        httpGetMethod(requestParams);
    }

    private void httpGetMethod(RequestParams requestParams) {
        final UserInfo userInfo = new UserInfo();
        HttpUtils.getRequest(Constants.WEIBO_GET_USER_INFO, requestParams,
                new JsonHttpResponseHandler() {
                    private Dialog progressDialogBar;

                    @Override
                    public void onFinish() {
                        if (progressDialogBar.isShowing()) {
                            progressDialogBar.dismiss();
                        }
                        // 隐藏进度条
                        super.onFinish();
                    }

                    @Override
                    public void onStart() {
                        progressDialogBar = ProgressDialogBar
                                .createDialog(mContext);
                        if (!progressDialogBar.isShowing()) {
                            progressDialogBar.show();
                        }
                        super.onStart();
                    }

                    @Override
                    public void onFailure(int statusCode, Header[] headers,
                                          String responseString, Throwable throwable) {
                        ToastUtil.show(R.string.notice_get_userinfo_fail);
                        // 防止加载数据过多，产生显示数据出现停顿
                        if (progressDialogBar != null
                                && progressDialogBar.isShowing()) {
                            progressDialogBar.dismiss();
                        }
                        super.onFailure(statusCode, headers, responseString,
                                throwable);
                    }

                    @Override
                    public void onSuccess(int statusCode, Header[] headers,
                                          JSONObject response) {
                        User user = User.parse(response.toString());
                        if (user != null) {
                            userInfo.setUser_id(user.id);
                            userInfo.setScreen_name(user.screen_name);
                            userInfo.setGender(user.gender);
                            userInfo.setUser_name(user.name);
                            userInfo.setToken(mAccessToken.getToken());
                            userInfo.setToken_secret(mAccessToken.getUid());
                            userInfo.setDescription(user.description);
                            userInfo.setLocation(user.location);
                            userInfo.setFavourites_count(Integer.valueOf(user.favourites_count).toString());
                            userInfo.setFollowers_count(Integer.valueOf(user.followers_count).toString());
                            userInfo.setFriends_count(Integer.valueOf(user.friends_count).toString());
                            userInfo.setStatuses_count(Integer.valueOf(user.statuses_count).toString());
                            userInfo.setUser_head(user.profile_image_url);
                            UserDao dao = new UserDao(OAuthActivity.this);
                            dao.insertUser(userInfo);
                            Toast.makeText(OAuthActivity.this,R.string.weibosdk_demo_toast_auth_success,Toast.LENGTH_SHORT).show();
                            JumpToNextStep("LoginWeibo");
                        } else {
                            Toast.makeText(OAuthActivity.this,R.string.weibosdk_demo_toast_auth_failed,Toast.LENGTH_SHORT).show();
                        }
                        super.onSuccess(statusCode, headers, response);
                    }
                });
    }

    /**
     * 跳转登录页面
     */
    protected void JumpToNextStep(String type) {
        if(type.equals("LoginWeibo")) {
            Intent intent = new Intent(this, LoginCircleActivity.class);
            startActivity(intent);
            finish();
        }else if(type.equals("LoginOther")) {
            startActivity(new Intent(this, TabMainActivity.class));
            finish();
        }
    }

    // 主菜单点击返回键，弹出对话框
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK
                && event.getAction() == KeyEvent.ACTION_DOWN) {
            if ((System.currentTimeMillis() - exitTime) > 2000) {
                Toast.makeText(getApplicationContext(), R.string.quit,Toast.LENGTH_SHORT).show();
                exitTime = System.currentTimeMillis();
            } else {
                finish();
                System.exit(0);
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    /**
     * auth callback interface
     **/
    private UMAuthListener umAuthListener = new UMAuthListener() {
        @Override
        public void onComplete(SHARE_MEDIA platform, int action, Map<String, String> data) {
            Toast.makeText(getApplicationContext(), "授权登录成功！", Toast.LENGTH_SHORT).show();
            if(platform == SHARE_MEDIA.QQ) {
                SharedPreferencesUtils.setParam(OAuthActivity.this, Global.LOGIN_TYPE, Global.LOGIN_TYPE_QQ);
            }else if(platform == SHARE_MEDIA.WEIXIN){
                SharedPreferencesUtils.setParam(OAuthActivity.this, Global.LOGIN_TYPE, Global.LOGIN_TYPE_WX);
            }
            com.umeng.socialize.utils.Log.d("user info", "user info:" + data.toString());
            JumpToNextStep("LoginOther");
        }

        @Override
        public void onError(SHARE_MEDIA platform, int action, Throwable t) {
            Toast.makeText(getApplicationContext(), "授权登录失败！", Toast.LENGTH_SHORT).show();
            SharedPreferencesUtils.setParam(OAuthActivity.this, Global.LOGIN_TYPE, Global.LOGIN_TYPE_NULL);
        }

        @Override
        public void onCancel(SHARE_MEDIA platform, int action) {
            Toast.makeText(getApplicationContext(), "授权登录取消！", Toast.LENGTH_SHORT).show();
            SharedPreferencesUtils.setParam(OAuthActivity.this, Global.LOGIN_TYPE, Global.LOGIN_TYPE_NULL);
        }
    };
    /**
     * delauth callback interface
     **/
    private UMAuthListener umdelAuthListener = new UMAuthListener() {
        @Override
        public void onComplete(SHARE_MEDIA platform, int action, Map<String, String> data) {
            Toast.makeText(getApplicationContext(), "delete Authorize succeed", Toast.LENGTH_SHORT).show();
            SharedPreferencesUtils.setParam(OAuthActivity.this, Global.LOGIN_TYPE, Global.LOGIN_TYPE_NULL);
        }

        @Override
        public void onError(SHARE_MEDIA platform, int action, Throwable t) {
            Toast.makeText(getApplicationContext(), "delete Authorize fail", Toast.LENGTH_SHORT).show();
            SharedPreferencesUtils.setParam(OAuthActivity.this, Global.LOGIN_TYPE, Global.LOGIN_TYPE_NULL);
        }

        @Override
        public void onCancel(SHARE_MEDIA platform, int action) {
            Toast.makeText(getApplicationContext(), "delete Authorize cancel", Toast.LENGTH_SHORT).show();
            SharedPreferencesUtils.setParam(OAuthActivity.this, Global.LOGIN_TYPE, Global.LOGIN_TYPE_NULL);
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(ready){
            SMSUtils.destroy();
        }
    }
}
