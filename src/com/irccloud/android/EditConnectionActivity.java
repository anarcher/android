/*
 * Copyright (c) 2013 IRCCloud, Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.irccloud.android;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Toast;

public class EditConnectionActivity extends ActionBarActivity {
    int reqid = -1;
    int cidToOpen = -1;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_connection);
        
        getSupportActionBar().setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM);
		getSupportActionBar().setBackgroundDrawable(getResources().getDrawable(R.drawable.heading_bg_blue));
		getSupportActionBar().setCustomView(R.layout.actionbar_edit_connection);

		FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        final EditConnectionFragment newFragment = new EditConnectionFragment();
        if(getIntent() != null && getIntent().hasExtra("cid"))
        	newFragment.setCid(getIntent().getIntExtra("cid", -1));
        if(getIntent() != null && getIntent().hasExtra("hostname"))
        	newFragment.default_hostname = getIntent().getStringExtra("hostname");
        if(getIntent() != null && getIntent().hasExtra("channels"))
        	newFragment.default_channels = getIntent().getStringExtra("channels");
    	newFragment.default_port = getIntent().getIntExtra("port", 6667);
        ft.add(R.id.EditConnectionFragment, newFragment);
        ft.commit();

        getSupportActionBar().getCustomView().findViewById(R.id.action_cancel).setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
                if(ServersDataSource.getInstance().count() < 1) {
                    NetworkConnection.getInstance().logout();
                    Intent i = new Intent(EditConnectionActivity.this, MainActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                }
				finish();
			}
        	
        });

        getSupportActionBar().getCustomView().findViewById(R.id.action_done).setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				reqid = newFragment.save();
			}
        	
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        NetworkConnection.getInstance().addHandler(mHandler);
    }

    @Override
    protected void onPause() {
        super.onPause();
        NetworkConnection.getInstance().removeHandler(mHandler);
    }

    private final Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            IRCCloudJSONObject obj;
            BuffersDataSource.Buffer buffer;
            switch (msg.what) {
                case NetworkConnection.EVENT_MAKEBUFFER:
                    buffer = (BuffersDataSource.Buffer)msg.obj;
                    if(buffer.cid == cidToOpen) {
                        Intent i = new Intent(EditConnectionActivity.this, MessageActivity.class);
                        i.putExtra("bid", buffer.bid);
                        startActivity(i);
                        finish();
                    }
                    break;
                case NetworkConnection.EVENT_SUCCESS:
                    obj = (IRCCloudJSONObject)msg.obj;
                    if(obj.getInt("_reqid") == reqid) {
                        cidToOpen = obj.cid();
                    }
                    break;
                case NetworkConnection.EVENT_FAILURE_MSG:
                    obj = (IRCCloudJSONObject)msg.obj;
                    if(obj.getInt("_reqid") == reqid) {
                        String message = obj.getString("message");
                        Toast.makeText(EditConnectionActivity.this, "Unable to add connection: invalid " + message, Toast.LENGTH_SHORT).show();
                    }
                    break;
                default:
                    break;
            }
        }
    };
}
