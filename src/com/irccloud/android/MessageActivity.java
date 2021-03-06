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

import java.util.ArrayList;
import java.util.HashMap;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.Region;
import android.graphics.drawable.Drawable;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.widget.ScrollerCompat;
import android.util.SparseArray;
import android.view.*;
import org.json.JSONException;
import org.json.JSONObject;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.text.util.Linkify;
import android.util.Log;
import android.view.View.OnFocusChangeListener;
import android.view.View.OnKeyListener;
import android.view.View.OnClickListener;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.TextView.OnEditorActionListener;

public class MessageActivity extends BaseActivity  implements UsersListFragment.OnUserSelectedListener, BuffersListFragment.OnBufferSelectedListener, MessageViewFragment.MessageViewListener {
	int cid = -1;
	int bid = -1;
	String name;
	String type;
	ActionEditText messageTxt;
	View sendBtn;
	int joined;
	int archived;
	String status;
	UsersDataSource.User selected_user;
	View userListView;
	View buffersListView;
	TextView title;
	TextView subtitle;
	ImageView key;
	LinearLayout messageContainer;
    DrawerLayout drawerLayout;
	NetworkConnection conn;
	private boolean shouldFadeIn = false;
	ImageView upView;
	private RefreshUpIndicatorTask refreshUpIndicatorTask = null;
	private ShowNotificationsTask showNotificationsTask = null;
	private ArrayList<Integer> backStack = new ArrayList<Integer>();
	PowerManager.WakeLock screenLock = null;
	private int launchBid = -1;
	private Uri launchURI = null;
	private AlertDialog channelsListDialog;
    String bufferToOpen = null;
    int cidToOpen = -1;

	private HashMap<Integer, EventsDataSource.Event> pendingEvents = new HashMap<Integer, EventsDataSource.Event>();
	
    @SuppressLint("NewApi")
	@SuppressWarnings({ "deprecation", "unchecked" })
	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
		getWindow().setBackgroundDrawable(null);
        setContentView(R.layout.activity_message);
        buffersListView = findViewById(R.id.BuffersList);
        messageContainer = (LinearLayout)findViewById(R.id.messageContainer);
        drawerLayout = (DrawerLayout)findViewById(R.id.drawerLayout);
        
        messageTxt = (ActionEditText)findViewById(R.id.messageTxt);
		messageTxt.setOnKeyListener(new OnKeyListener() {
			@Override
			public boolean onKey(View v, int keyCode, KeyEvent event) {
				if(event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER && messageTxt.getText() != null && messageTxt.getText().length() > 0) {
	         		   new SendTask().execute((Void)null);
				}
				return false;
			}
		});
		messageTxt.setOnFocusChangeListener(new OnFocusChangeListener() {
			@Override
			public void onFocusChange(View v, boolean hasFocus) {
				if(drawerLayout != null && v == messageTxt && hasFocus) {
                    drawerLayout.closeDrawers();
		        	upView.setVisibility(View.VISIBLE);
				}
			}
		});
		messageTxt.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if(drawerLayout != null) {
                    drawerLayout.closeDrawers();
		        	upView.setVisibility(View.VISIBLE);
				}
			}
		});
        messageTxt.setOnEditorActionListener(new OnEditorActionListener() {
            public boolean onEditorAction(TextView view, int actionId, KeyEvent event) {
         	   if(NetworkConnection.getInstance().getState() == NetworkConnection.STATE_CONNECTED && actionId == EditorInfo.IME_ACTION_SEND && messageTxt.getText() != null && messageTxt.getText().length() > 0) {
         		   new SendTask().execute((Void)null);
         	   }
         	   return true;
           	}
        });
        messageTxt.addTextChangedListener(new TextWatcher() {
            public void afterTextChanged(Editable s) {
            	Object[] spans = s.getSpans(0, s.length(), Object.class);
            	for(Object o : spans) {
            		if(((s.getSpanFlags(o) & Spanned.SPAN_COMPOSING) != Spanned.SPAN_COMPOSING) && (o.getClass() == StyleSpan.class || o.getClass() == ForegroundColorSpan.class || o.getClass() == BackgroundColorSpan.class || o.getClass() == UnderlineSpan.class)) {
            			s.removeSpan(o);
            		}
            	}
            	if(s.length() > 0 && NetworkConnection.getInstance().getState() == NetworkConnection.STATE_CONNECTED) {
	           		sendBtn.setEnabled(true);
	           		if(Build.VERSION.SDK_INT >= 11)
	           			sendBtn.setAlpha(1);
            	} else {
	           		sendBtn.setEnabled(false);
	           		if(Build.VERSION.SDK_INT >= 11)
	           			sendBtn.setAlpha(0.5f);
            	}
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
        });
        sendBtn = findViewById(R.id.sendBtn);
        sendBtn.setFocusable(false);
        sendBtn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if(NetworkConnection.getInstance().getState() == NetworkConnection.STATE_CONNECTED)
					new SendTask().execute((Void)null);
			}
        });
        userListView = findViewById(R.id.usersListFragment);
        
        getSupportActionBar().setLogo(R.drawable.logo);
        getSupportActionBar().setHomeButtonEnabled(false);
       	getSupportActionBar().setDisplayHomeAsUpEnabled(false);
        getSupportActionBar().setDisplayShowTitleEnabled(false);
        
        View v = getLayoutInflater().inflate(R.layout.actionbar_messageview, null);
        v.findViewById(R.id.actionTitleArea).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
            	ChannelsDataSource.Channel c = ChannelsDataSource.getInstance().getChannelForBuffer(bid);
            	if(c != null) {
            		AlertDialog.Builder builder = new AlertDialog.Builder(MessageActivity.this);
	            	builder.setTitle("Channel Topic");
	            	if(c.topic_text.length() > 0) {
	            		builder.setMessage(ColorFormatter.html_to_spanned(ColorFormatter.irc_to_html(TextUtils.htmlEncode(c.topic_text)), true, ServersDataSource.getInstance().getServer(cid)));
	            	} else
	            		builder.setMessage("No topic set.");
	            	builder.setPositiveButton("Close", new DialogInterface.OnClickListener() {

						@Override
						public void onClick(DialogInterface dialog, int which) {
							dialog.dismiss();
						}
	            	});
	            	boolean canEditTopic;
	            	if(c.mode.contains("t")) {
	            		UsersDataSource.User self_user = UsersDataSource.getInstance().getUser(cid, bid, ServersDataSource.getInstance().getServer(cid).nick);
	            		if(self_user != null && (self_user.mode.contains("q") || self_user.mode.contains("a") || self_user.mode.contains("o"))) {
	            			canEditTopic = true;
	            		} else {
	            			canEditTopic = false;
	            		}
	            	} else {
            			canEditTopic = true;
	            	}
	            	
	            	if(canEditTopic) {
		            	builder.setNeutralButton("Edit", new DialogInterface.OnClickListener() {
	
							@Override
							public void onClick(DialogInterface dialog, int which) {
								dialog.dismiss();
								editTopic();
							}
		            	});
	            	}
		    		final AlertDialog dialog = builder.create();
		    		dialog.setOwnerActivity(MessageActivity.this);
		    		dialog.show();
		    		((TextView)dialog.findViewById(android.R.id.message)).setMovementMethod(LinkMovementMethod.getInstance());
		    		((TextView)dialog.findViewById(android.R.id.message)).setOnClickListener(new OnClickListener() {

						@Override
						public void onClick(View v) {
							dialog.dismiss();
						}
		    			
		    		});
            	} else if(archived == 0 && subtitle.getText().length() > 0){
            		AlertDialog.Builder builder = new AlertDialog.Builder(MessageActivity.this);
	            	builder.setTitle(title.getText().toString());
            		final SpannableString s = new SpannableString(subtitle.getText().toString());
            		Linkify.addLinks(s, Linkify.WEB_URLS | Linkify.EMAIL_ADDRESSES);
            		builder.setMessage(s);
	            	builder.setPositiveButton("Close", new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							dialog.dismiss();
						}
	            	});
		    		AlertDialog dialog = builder.create();
		    		dialog.setOwnerActivity(MessageActivity.this);
		    		dialog.show();
		    		((TextView)dialog.findViewById(android.R.id.message)).setMovementMethod(LinkMovementMethod.getInstance());
            	}
			}
        });

        upView = (ImageView)v.findViewById(R.id.upIndicator);
        if(drawerLayout != null) {
            drawerLayout.setDrawerListener(mDrawerListener);
        	upView.setVisibility(View.VISIBLE);
        	upView.setOnClickListener(upClickListener);
	        ImageView icon = (ImageView)v.findViewById(R.id.upIcon);
	        icon.setOnClickListener(upClickListener);
	        if(refreshUpIndicatorTask != null)
	        	refreshUpIndicatorTask.cancel(true);
	        refreshUpIndicatorTask = new RefreshUpIndicatorTask();
	        refreshUpIndicatorTask.execute((Void)null);
        } else {
        	upView.setVisibility(View.INVISIBLE);
        }
		messageTxt.setDrawerLayout(drawerLayout, upView);

        title = (TextView)v.findViewById(R.id.title);
        subtitle = (TextView)v.findViewById(R.id.subtitle);
        key = (ImageView)v.findViewById(R.id.key);
        getSupportActionBar().setCustomView(v);
        
        if(savedInstanceState != null && savedInstanceState.containsKey("cid")) {
        	cid = savedInstanceState.getInt("cid");
        	bid = savedInstanceState.getInt("bid");
        	name = savedInstanceState.getString("name");
        	type = savedInstanceState.getString("type");
        	joined = savedInstanceState.getInt("joined");
        	archived = savedInstanceState.getInt("archived");
        	status = savedInstanceState.getString("status");
        	backStack = (ArrayList<Integer>) savedInstanceState.getSerializable("backStack");
        }
        if(getSharedPreferences("prefs", 0).contains("session_key") && Config.GCM_ID.length() > 0 && checkPlayServices()) {
            final String regId = GCMIntentService.getRegistrationId(this);
            if (regId.equals("")) {
                GCMIntentService.scheduleRegisterTimer(1000);
            } else {
                if(!getSharedPreferences("prefs", 0).contains("gcm_registered"))
                    GCMIntentService.scheduleRegisterTimer(30000);
            }
        }
    }

    @Override
    public void onSaveInstanceState(Bundle state) {
    	super.onSaveInstanceState(state);
    	state.putInt("cid", cid);
    	state.putInt("bid", bid);
    	state.putString("name", name);
    	state.putString("type", type);
    	state.putInt("joined", joined);
    	state.putInt("archived", archived);
    	state.putString("status", status);
    	state.putSerializable("backStack", backStack);
    }
    
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) { //Back key pressed
        	if(drawerLayout != null && (drawerLayout.isDrawerOpen(Gravity.LEFT) || drawerLayout.isDrawerOpen(Gravity.RIGHT))) {
                drawerLayout.closeDrawers();
	        	upView.setVisibility(View.VISIBLE);
	        	return true;
        	}
            while(backStack != null && backStack.size() > 0) {
        		Integer bid = backStack.get(0);
        		backStack.remove(0);
        		BuffersDataSource.Buffer buffer = BuffersDataSource.getInstance().getBuffer(bid);
        		if(buffer != null) {
    				ServersDataSource.Server s = ServersDataSource.getInstance().getServer(buffer.cid);
    				if(s != null)
    					status = s.status;
            		String name = buffer.name;
        			if(buffer.type.equalsIgnoreCase("console")) {
        				if(s != null) {
	        				if(s.name != null && s.name.length() > 0)
	        					name = s.name;
	        				else
	        					name = s.hostname;
        				}
        			}
	    			onBufferSelected(buffer.cid, buffer.bid, name, buffer.last_seen_eid, buffer.min_eid, 
	    					buffer.type, 1, buffer.archived, status);
	    			if(backStack.size() > 0)
	    				backStack.remove(0);
                    return true;
        		}
        	}
            return super.onKeyDown(keyCode, event);
        }
        return super.onKeyDown(keyCode, event);
    }
    
    private class SendTask extends AsyncTaskEx<Void, Void, Void> {
    	EventsDataSource.Event e = null;
    	
    	@Override
    	protected void onPreExecute() {
	    	MessageViewFragment mvf = (MessageViewFragment)getSupportFragmentManager().findFragmentById(R.id.messageViewFragment);
	    	if(mvf != null && mvf.bid != bid) {
	    		Log.w("IRCCloud", "MessageViewFragment's bid differs from MessageActivity's, switching buffers again");
	    		open_bid(mvf.bid);
	    	}
	    	
			if(conn != null && conn.getState() == NetworkConnection.STATE_CONNECTED && messageTxt.getText() != null && messageTxt.getText().length() > 0) {
	    		ServersDataSource.Server s = ServersDataSource.getInstance().getServer(cid);
	    		if(s != null) {
		    		sendBtn.setEnabled(false);
	           		if(Build.VERSION.SDK_INT >= 11)
	           			sendBtn.setAlpha(0.5f);
		    		UsersDataSource.User u = UsersDataSource.getInstance().getUser(cid, bid, s.nick);
		    		e = EventsDataSource.getInstance().new Event();
		    		e.cid = cid;
		    		e.bid = bid;
		    		e.eid = (System.currentTimeMillis() + conn.clockOffset + 5000) * 1000L;
		    		e.self = true;
		    		e.from = s.nick;
		    		e.nick = s.nick;
		    		if(u != null)
		    			e.from_mode = u.mode;
		    		String msg = messageTxt.getText().toString();
		    		if(msg.startsWith("//"))
		    			msg = msg.substring(1);
		    		else if(msg.startsWith("/") && !msg.startsWith("/me "))
		    			msg = null;
		    		e.msg = msg;
		    		if(msg != null && msg.toLowerCase().startsWith("/me ")) {
			    		e.type = "buffer_me_msg";
			    		e.msg = msg.substring(4);
		    		} else {
			    		e.type = "buffer_msg";
		    		}
					e.color = R.color.timestamp;
					if(name.equals(s.nick))
						e.bg_color = R.color.message_bg;
					else
						e.bg_color = R.color.self;
			    	e.row_type = 0;
			    	e.html = null;
			    	e.group_msg = null;
			    	e.linkify = true;
			    	e.target_mode = null;
			    	e.highlight = false;
			    	e.reqid = -1;
			    	e.pending = true;
			    	if(e.msg != null) {
			    		e.msg = TextUtils.htmlEncode(e.msg);
			    		EventsDataSource.getInstance().addEvent(e);
			    		conn.notifyHandlers(NetworkConnection.EVENT_BUFFERMSG, e, mHandler);
			    	}
				}
			}
    	}
    	
		@Override
		protected Void doInBackground(Void... arg0) {
			if(e != null && conn != null && conn.getState() == NetworkConnection.STATE_CONNECTED && messageTxt.getText() != null && messageTxt.getText().length() > 0) {
				e.reqid = conn.say(cid, name, messageTxt.getText().toString());
				if(e.msg != null)
					pendingEvents.put(e.reqid, e);
			}
			return null;
		}
    	
		@Override
		protected void onPostExecute(Void result) {
			if(e != null && e.reqid != -1) {
				messageTxt.setText("");
                BuffersDataSource.getInstance().updateDraft(e.bid, null);
			} else {
                sendBtn.setEnabled(true);
                if(Build.VERSION.SDK_INT >= 11)
                    sendBtn.setAlpha(1);
            }
		}
    }
    
    private class RefreshUpIndicatorTask extends AsyncTaskEx<Void, Void, Void> {
		int unread = 0;
		int highlights = 0;
    	
		@Override
		protected Void doInBackground(Void... arg0) {
            if(drawerLayout != null) {
                SparseArray<ServersDataSource.Server> servers = ServersDataSource.getInstance().getServers();

                JSONObject channelDisabledMap = null;
                JSONObject bufferDisabledMap = null;
                if(NetworkConnection.getInstance().getUserInfo() != null && NetworkConnection.getInstance().getUserInfo().prefs != null) {
                    try {
                        if(NetworkConnection.getInstance().getUserInfo().prefs.has("channel-disableTrackUnread"))
                            channelDisabledMap = NetworkConnection.getInstance().getUserInfo().prefs.getJSONObject("channel-disableTrackUnread");
                        if(NetworkConnection.getInstance().getUserInfo().prefs.has("buffer-disableTrackUnread"))
                            bufferDisabledMap = NetworkConnection.getInstance().getUserInfo().prefs.getJSONObject("buffer-disableTrackUnread");
                    } catch (Exception e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }

                for(int i = 0; i < servers.size(); i++) {
                    ServersDataSource.Server s = servers.valueAt(i);
                    ArrayList<BuffersDataSource.Buffer> buffers = BuffersDataSource.getInstance().getBuffersForServer(s.cid);
                    for(int j = 0; j < buffers.size(); j++) {
                        BuffersDataSource.Buffer b = buffers.get(j);
                        if(b.bid != bid) {
                            if(unread == 0) {
                                int u = 0;
                                try {
                                    u = EventsDataSource.getInstance().getUnreadCountForBuffer(b.bid, b.last_seen_eid, b.type);
                                    if(b.type.equalsIgnoreCase("channel") && channelDisabledMap != null && channelDisabledMap.has(String.valueOf(b.bid)) && channelDisabledMap.getBoolean(String.valueOf(b.bid)))
                                        u = 0;
                                    else if(bufferDisabledMap != null && bufferDisabledMap.has(String.valueOf(b.bid)) && bufferDisabledMap.getBoolean(String.valueOf(b.bid)))
                                        u = 0;
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }
                                unread += u;
                            }
                            if(highlights == 0) {
                                try {
                                    if(!b.type.equalsIgnoreCase("conversation") || bufferDisabledMap == null || !bufferDisabledMap.has(String.valueOf(b.bid)) || !bufferDisabledMap.getBoolean(String.valueOf(b.bid)))
                                        highlights += EventsDataSource.getInstance().getHighlightCountForBuffer(b.bid, b.last_seen_eid, b.type);
                                } catch (JSONException e) {
                                }
                            }
                        }
                    }
                }
            }
			return null;
		}
    	
		@Override
		protected void onPostExecute(Void result) {
			if(!isCancelled() && drawerLayout != null) {
				if(highlights > 0) {
                    mDrawerListener.setUpDrawable(getResources().getDrawable(R.drawable.ic_navigation_drawer_highlight));
				} else if(unread > 0) {
                    mDrawerListener.setUpDrawable(getResources().getDrawable(R.drawable.ic_navigation_drawer_unread));
				} else {
                    mDrawerListener.setUpDrawable(getResources().getDrawable(R.drawable.ic_navigation_drawer));
				}
				refreshUpIndicatorTask = null;
			}
		}
    }
    
    private class ShowNotificationsTask extends AsyncTaskEx<Integer, Void, Void> {

		@Override
		protected Void doInBackground(Integer... params) {
	    	Notifications.getInstance().excludeBid(params[0]);
	    	if(params[0] > 0)
	    		Notifications.getInstance().showNotifications(null);
	    	showNotificationsTask = null;
			return null;
		}
    }
    
    private void setFromIntent(Intent intent) {
    	long min_eid = 0;
    	long last_seen_eid = 0;
    	
    	launchBid = -1;
    	launchURI = null;

    	setIntent(null);
    	
    	if(intent.hasExtra("bid")) {
    		int new_bid = intent.getIntExtra("bid", 0);
    		if(NetworkConnection.getInstance().ready && BuffersDataSource.getInstance().getBuffer(new_bid) == null) {
    			Log.w("IRCCloud", "Invalid bid requested by launch intent: " + new_bid);
    			Notifications.getInstance().deleteNotificationsForBid(new_bid);
    			if(showNotificationsTask != null)
    				showNotificationsTask.cancel(true);
    			showNotificationsTask = new ShowNotificationsTask();
    			showNotificationsTask.execute(bid);
    			return;
    		} else {
    	    	if(bid >= 0)
    	    		backStack.add(0, bid);
    			bid = new_bid;
    		}
    	}
    	
    	if(intent.getData() != null && intent.getData().getScheme() != null && intent.getData().getScheme().startsWith("irc")) {
    		if(open_uri(intent.getData()))
    			return;
    		else
    			launchURI = intent.getData();
    	} else if(intent.hasExtra("cid")) {
	    	cid = intent.getIntExtra("cid", 0);
	    	name = intent.getStringExtra("name");
	    	type = intent.getStringExtra("type");
	    	joined = intent.getIntExtra("joined", 0);
	    	archived = intent.getIntExtra("archived", 0);
	    	status = intent.getStringExtra("status");
	    	min_eid = intent.getLongExtra("min_eid", 0);
	    	last_seen_eid = intent.getLongExtra("last_seen_eid", 0);
	    	
	    	if(bid == -1) {
	    		BuffersDataSource.Buffer b = BuffersDataSource.getInstance().getBufferByName(cid, name);
	    		if(b != null) {
	    			bid = b.bid;
	    			last_seen_eid = b.last_seen_eid;
	    			min_eid = b.min_eid;
	    			archived = b.archived;
	    		}
	    	}
    	} else if(bid != -1) {
    		BuffersDataSource.Buffer b = BuffersDataSource.getInstance().getBuffer(bid);
			ServersDataSource.Server s = ServersDataSource.getInstance().getServer((b==null)?-1:b.cid);
    		if(b != null && s != null) {
				joined = 1;
				if(b.type.equalsIgnoreCase("channel")) {
					ChannelsDataSource.Channel c = ChannelsDataSource.getInstance().getChannelForBuffer(b.bid);
					if(c == null)
						joined = 0;
				}
				if(b.type.equalsIgnoreCase("console"))
					b.name = s.name;
	    		cid = b.cid;
	    		name = b.name;
	    		type = b.type;
	    		archived = b.archived;
	    		min_eid = b.min_eid;
	    		last_seen_eid = b.last_seen_eid;
	    		status = s.status;
    		} else {
    			cid = -1;
    		}
    	}

    	if(cid == -1) {
			launchBid = bid;
    	} else {
	    	UsersListFragment ulf = (UsersListFragment)getSupportFragmentManager().findFragmentById(R.id.usersListFragment);
	    	MessageViewFragment mvf = (MessageViewFragment)getSupportFragmentManager().findFragmentById(R.id.messageViewFragment);
	    	Bundle b = new Bundle();
	    	b.putInt("cid", cid);
	    	b.putInt("bid", bid);
	    	b.putLong("last_seen_eid", last_seen_eid);
	    	b.putLong("min_eid", min_eid);
	    	b.putString("name", name);
	    	b.putString("type", type);
	    	ulf.setArguments(b);
	    	mvf.setArguments(b);
    	}
    }
    
    @Override
    protected void onNewIntent(Intent intent) {
    	if(intent != null) {
    		setFromIntent(intent);
    	}
    }
    
    @SuppressLint("NewApi")
	@Override
    public void onResume() {
    	conn = NetworkConnection.getInstance();
    	if(!conn.ready) {
        	super.onResume();
    		Intent i = new Intent(this, MainActivity.class);
    		if(getIntent() != null) {
    			if(getIntent().getData() != null)
    				i.setData(getIntent().getData());
    			if(getIntent().getExtras() != null)
    				i.putExtras(getIntent().getExtras());
    		}
    		startActivity(i);
    		finish();
    		return;
    	}
    	conn.addHandler(mHandler);

    	super.onResume();
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
    	if(prefs.getBoolean("screenlock", false)) {
    		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    	} else {
    		getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    	}
    	
    	if(conn.getState() != NetworkConnection.STATE_CONNECTED) {
    		if(drawerLayout != null && !NetworkConnection.getInstance().ready) {
                drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
            	upView.setVisibility(View.INVISIBLE);
    		}
    		sendBtn.setEnabled(false);
       		if(Build.VERSION.SDK_INT >= 11)
       			sendBtn.setAlpha(0.5f);
    	} else {
    		if(drawerLayout != null) {
                drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
            	upView.setVisibility(View.VISIBLE);
    		}
    		if(messageTxt.getText() != null && messageTxt.getText().length() > 0) {
    			sendBtn.setEnabled(true);
           		if(Build.VERSION.SDK_INT >= 11)
           			sendBtn.setAlpha(1);
    		}
    	}

    	if(cid == -1 || launchURI != null) {
    		if(getIntent() != null && (getIntent().hasExtra("bid") || getIntent().getData() != null)) {
	    		setFromIntent(getIntent());
	    	} else if(conn.getState() == NetworkConnection.STATE_CONNECTED && conn.getUserInfo() != null && conn.ready) {
	    		if(launchURI == null || !open_uri(launchURI)) {
		    		if(!open_bid(conn.getUserInfo().last_selected_bid)) {
		    			if(!open_bid(BuffersDataSource.getInstance().firstBid())) {
		    				if(drawerLayout != null && NetworkConnection.getInstance().ready)
                                drawerLayout.openDrawer(Gravity.LEFT);
                        }
		    		}
	    		}
	    	}
    	}

    	updateUsersListFragmentVisibility();
    	title.setText(name);
    	getSupportActionBar().setTitle(name);
    	update_subtitle();

    	if(getSupportFragmentManager().findFragmentById(R.id.BuffersList) != null)
    		((BuffersListFragment)getSupportFragmentManager().findFragmentById(R.id.BuffersList)).setSelectedBid(bid);
    	
        if(refreshUpIndicatorTask != null)
        	refreshUpIndicatorTask.cancel(true);
        refreshUpIndicatorTask = new RefreshUpIndicatorTask();
        refreshUpIndicatorTask.execute((Void)null);

    	supportInvalidateOptionsMenu();
    	
    	if(NetworkConnection.getInstance().ready) {
			if(showNotificationsTask != null)
				showNotificationsTask.cancel(true);
			showNotificationsTask = new ShowNotificationsTask();
			showNotificationsTask.execute(bid);
    	}
   		sendBtn.setEnabled(messageTxt.getText().length() > 0);
   		if(Build.VERSION.SDK_INT >= 11 && messageTxt.getText().length() == 0)
   			sendBtn.setAlpha(0.5f);

        if(drawerLayout != null)
            drawerLayout.closeDrawers();
    }

    @Override
    public void onPause() {
    	super.onPause();
		if(showNotificationsTask != null)
			showNotificationsTask.cancel(true);
		showNotificationsTask = new ShowNotificationsTask();
		showNotificationsTask.execute(-1);
		if(channelsListDialog != null)
			channelsListDialog.dismiss();
    	if(conn != null)
    		conn.removeHandler(mHandler);
    	conn = null;
    }
	
    private boolean open_uri(Uri uri) {
		if(uri != null && conn != null && conn.ready) {
			launchURI = null;
    		ServersDataSource.Server s = null;
    		if(uri.getPort() > 0)
    			s = ServersDataSource.getInstance().getServer(uri.getHost(), uri.getPort());
			else if(uri.getScheme().equalsIgnoreCase("ircs"))
    			s = ServersDataSource.getInstance().getServer(uri.getHost(), true);
    		else
    			s = ServersDataSource.getInstance().getServer(uri.getHost());

    		if(s != null) {
    			if(uri.getPath().length() > 1) {
	    			String key = null;
	    			String channel = uri.getPath().substring(1);
	    			if(channel.contains(",")) {
	    				key = channel.substring(channel.indexOf(",") + 1);
	    				channel = channel.substring(0, channel.indexOf(","));
	    			}
	    			BuffersDataSource.Buffer b = BuffersDataSource.getInstance().getBufferByName(s.cid, channel);
	    			if(b != null) {
                        cid = -1;
	    				return open_bid(b.bid);
                    } else {
	    				conn.join(s.cid, channel, key);
                    }
	    			return true;
    			} else {
	    			BuffersDataSource.Buffer b = BuffersDataSource.getInstance().getBufferByName(s.cid, "*");
	    			if(b != null)
	    				return open_bid(b.bid);
    			}
    		} else {
				if(getWindowManager().getDefaultDisplay().getWidth() < 800) {
					Intent i = new Intent(this, EditConnectionActivity.class);
					i.putExtra("hostname", uri.getHost());
					if(uri.getPort() > 0)
						i.putExtra("port", uri.getPort());
	    			else if(uri.getScheme().equalsIgnoreCase("ircs"))
	    				i.putExtra("port", 6697);
	    			if(uri.getPath().length() > 1)
	    				i.putExtra("channels", uri.getPath().substring(1).replace(",", " "));
					startActivity(i);
				} else {
		        	EditConnectionFragment connFragment = new EditConnectionFragment();
		        	connFragment.default_hostname = uri.getHost();
	    			if(uri.getPort() > 0)
	    				connFragment.default_port = uri.getPort();
	    			else if(uri.getScheme().equalsIgnoreCase("ircs"))
	    				connFragment.default_port = 6697;
	    			if(uri.getPath().length() > 1)
	    				connFragment.default_channels = uri.getPath().substring(1).replace(",", " ");
		            connFragment.show(getSupportFragmentManager(), "addnetwork");
				}
	            return true;
    		}
		}
		return false;
    }
    
    private boolean open_bid(int bid) {
		BuffersDataSource.Buffer b = BuffersDataSource.getInstance().getBuffer(bid);
		if(b != null) {
			ServersDataSource.Server s = ServersDataSource.getInstance().getServer(b.cid);
			int joined = 1;
			if(b.type.equalsIgnoreCase("channel")) {
				ChannelsDataSource.Channel c = ChannelsDataSource.getInstance().getChannelForBuffer(b.bid);
				if(c == null)
					joined = 0;
			}
			String name = b.name;
			if(b.type.equalsIgnoreCase("console")) {
				if(s.name != null && s.name.length() > 0)
					name = s.name;
				else
					name = s.hostname;
			}
			onBufferSelected(b.cid, b.bid, name, b.last_seen_eid, b.min_eid, b.type, joined, b.archived, s.status);
			return true;
		}
		Log.w("IRCCloud", "Requested BID not found");
		return false;
    }
    
    private void update_subtitle() {
    	if(cid == -1 || !NetworkConnection.getInstance().ready) {
           	getSupportActionBar().setDisplayShowHomeEnabled(true);
            getSupportActionBar().setDisplayShowCustomEnabled(false);
    	} else {
           	getSupportActionBar().setDisplayShowHomeEnabled(false);
            getSupportActionBar().setDisplayShowCustomEnabled(true);

            if(archived > 0 && !type.equalsIgnoreCase("console")) {
	    		subtitle.setVisibility(View.VISIBLE);
	    		subtitle.setText("(archived)");
	    	} else {
	    		if(type == null) {
	        		subtitle.setVisibility(View.GONE);
	    		} else if(type.equalsIgnoreCase("conversation")) {
	        		UsersDataSource.User user = UsersDataSource.getInstance().getUser(cid, name);
	    			BuffersDataSource.Buffer b = BuffersDataSource.getInstance().getBuffer(bid);
	    			if(user != null && user.away > 0) {
		        		subtitle.setVisibility(View.VISIBLE);
	    				if(user.away_msg != null && user.away_msg.length() > 0) {
	    					subtitle.setText("Away: " + ColorFormatter.html_to_spanned(ColorFormatter.irc_to_html(TextUtils.htmlEncode(user.away_msg))).toString());
	    				} else if(b != null && b.away_msg != null && b.away_msg.length() > 0) {
	    	        		subtitle.setText("Away: " + ColorFormatter.html_to_spanned(ColorFormatter.irc_to_html(TextUtils.htmlEncode(b.away_msg))).toString());
	    				} else {
	    					subtitle.setText("Away");
	    				}
	    			} else {
		        		subtitle.setVisibility(View.GONE);
	    			}
	        		key.setVisibility(View.GONE);
	    		} else if(type.equalsIgnoreCase("channel")) {
		        	ChannelsDataSource.Channel c = ChannelsDataSource.getInstance().getChannelForBuffer(bid);
		        	if(c != null && c.topic_text.length() > 0) {
		        		subtitle.setVisibility(View.VISIBLE);
		        		subtitle.setText(ColorFormatter.html_to_spanned(ColorFormatter.irc_to_html(TextUtils.htmlEncode(c.topic_text)), false, null).toString());
		        	} else {
		        		subtitle.setVisibility(View.GONE);
		        	}
		        	if(c != null && c.key) {
                        key.setImageResource(R.drawable.lock);
		        		key.setVisibility(View.VISIBLE);
		        	} else {
		        		key.setVisibility(View.GONE);
		        	}
	    		} else if(type.equalsIgnoreCase("console")) {
	    			ServersDataSource.Server s = ServersDataSource.getInstance().getServer(cid);
	    			if(s != null) {
		        		subtitle.setVisibility(View.VISIBLE);
		        		subtitle.setText(s.hostname + ":" + s.port);
		        	} else {
		        		subtitle.setVisibility(View.GONE);
	    			}
                    if(s != null && s.ssl > 0)
                        key.setImageResource(R.drawable.world_shield);
                    else
                        key.setImageResource(R.drawable.world);
	        		key.setVisibility(View.VISIBLE);
	    		}
	    	}
    	}
    	supportInvalidateOptionsMenu();
    }
    
    private void updateUsersListFragmentVisibility() {
    	boolean hide = false;
		if(userListView != null) {
			try {
				if(conn != null && conn.getUserInfo() != null && conn.getUserInfo().prefs != null) {
					JSONObject hiddenMap = conn.getUserInfo().prefs.getJSONObject("channel-hiddenMembers");
					if(hiddenMap.has(String.valueOf(bid)) && hiddenMap.getBoolean(String.valueOf(bid)))
						hide = true;
				}
			} catch (Exception e) {
			}
	    	if(hide || type == null || !type.equalsIgnoreCase("channel") || joined == 0) {
	    		userListView.setVisibility(View.GONE);
                if(drawerLayout != null)
                    drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, Gravity.RIGHT);
            } else {
	    		userListView.setVisibility(View.VISIBLE);
                if(drawerLayout != null)
                    drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, Gravity.RIGHT);
            }
		}
    }
    
	@SuppressLint("HandlerLeak")
	private final Handler mHandler = new Handler() {

		public void handleMessage(Message msg) {
			Integer event_bid = 0;
			IRCCloudJSONObject event = null;
			Bundle args = null;
			switch (msg.what) {
			case NetworkConnection.EVENT_LINKCHANNEL:
				event = (IRCCloudJSONObject)msg.obj;
				if(cidToOpen == event.cid() && event.getString("invalid_chan").equalsIgnoreCase(bufferToOpen)) {
					bufferToOpen = event.getString("valid_chan");
					msg.obj = BuffersDataSource.getInstance().getBuffer(event.bid());
				} else {
					return;
				}
			case NetworkConnection.EVENT_MAKEBUFFER:
				BuffersDataSource.Buffer b = (BuffersDataSource.Buffer)msg.obj;
				if(cidToOpen == b.cid && b.name.equalsIgnoreCase(bufferToOpen) && !bufferToOpen.equalsIgnoreCase(name)) {
                    ServersDataSource.Server s = ServersDataSource.getInstance().getServer(b.cid);
                    cid = -1;
					onBufferSelected(b.cid, b.bid, (b.type.equalsIgnoreCase("console"))?s.name:b.name, b.last_seen_eid, b.min_eid, b.type, 1, 0, "connected_ready");
		    		bufferToOpen = null;
		    		cidToOpen = -1;
				} else if(bid == -1 && b.cid == cid && b.name.equalsIgnoreCase(name)) {
					bid = b.bid;
			    	if(getSupportFragmentManager().findFragmentById(R.id.BuffersList) != null)
			    		((BuffersListFragment)getSupportFragmentManager().findFragmentById(R.id.BuffersList)).setSelectedBid(bid);
	    			if(showNotificationsTask != null)
	    				showNotificationsTask.cancel(true);
	    			showNotificationsTask = new ShowNotificationsTask();
	    			showNotificationsTask.execute(bid);
				}
				break;
			case NetworkConnection.EVENT_OPENBUFFER:
				event = (IRCCloudJSONObject)msg.obj;
				try {
					bufferToOpen = event.getString("name");
					cidToOpen = event.cid();
					b = BuffersDataSource.getInstance().getBufferByName(cidToOpen, bufferToOpen);
					if(b != null && !bufferToOpen.equalsIgnoreCase(name)) {
                        cid = -1;
						onBufferSelected(b.cid, b.bid, b.name, b.last_seen_eid, b.min_eid, b.type, 1, 0, "connected_ready");
			    		bufferToOpen = null;
			    		cidToOpen = -1;
					}
				} catch (Exception e2) {
					// TODO Auto-generated catch block
					e2.printStackTrace();
				}
				break;
			case NetworkConnection.EVENT_CONNECTIVITY:
				if(conn != null) {
					if(conn.getState() == NetworkConnection.STATE_CONNECTED) {
						for(EventsDataSource.Event e : pendingEvents.values()) {
							EventsDataSource.getInstance().deleteEvent(e.eid, e.bid);
						}
						pendingEvents.clear();
			    		if(drawerLayout != null && NetworkConnection.getInstance().ready) {
                            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
							upView.setVisibility(View.VISIBLE);
                            updateUsersListFragmentVisibility();
			    		}
			    		if(cid != -1 && messageTxt.getText() != null && messageTxt.getText().length() > 0) {
			    			sendBtn.setEnabled(true);
			           		if(Build.VERSION.SDK_INT >= 11)
			           			sendBtn.setAlpha(1);
			    		}
					} else {
			    		if(drawerLayout != null && !NetworkConnection.getInstance().ready) {
                            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
			        		upView.setVisibility(View.INVISIBLE);
			    		}
			    		sendBtn.setEnabled(false);
		           		if(Build.VERSION.SDK_INT >= 11)
		           			sendBtn.setAlpha(0.5f);
					}
				}
				break;
			case NetworkConnection.EVENT_BANLIST:
				event = (IRCCloudJSONObject)msg.obj;
				if(event.getString("channel").equalsIgnoreCase(name)) {
	            	args = new Bundle();
	            	args.putInt("cid", cid);
	            	args.putInt("bid", bid);
	            	args.putString("event", event.toString());
	            	BanListFragment banList = (BanListFragment)getSupportFragmentManager().findFragmentByTag("banlist");
	            	if(banList == null) {
	            		banList = new BanListFragment();
			        	banList.setArguments(args);
			        	banList.show(getSupportFragmentManager(), "banlist");
	            	} else {
			        	banList.setArguments(args);
	            	}
				}
	            break;
			case NetworkConnection.EVENT_ACCEPTLIST:
				event = (IRCCloudJSONObject)msg.obj;
				if(event.cid() == cid) {
	            	args = new Bundle();
	            	args.putInt("cid", cid);
	            	args.putString("event", event.toString());
	            	AcceptListFragment acceptList = (AcceptListFragment)getSupportFragmentManager().findFragmentByTag("acceptlist");
	            	if(acceptList == null) {
	            		acceptList = new AcceptListFragment();
			        	acceptList.setArguments(args);
			        	acceptList.show(getSupportFragmentManager(), "acceptlist");
	            	} else {
			        	acceptList.setArguments(args);
	            	}
				}
	            break;
			case NetworkConnection.EVENT_WHOLIST:
				event = (IRCCloudJSONObject)msg.obj;
            	args = new Bundle();
            	args.putString("event", event.toString());
            	WhoListFragment whoList = (WhoListFragment)getSupportFragmentManager().findFragmentByTag("wholist");
            	if(whoList == null) {
            		whoList = new WhoListFragment();
            		whoList.setArguments(args);
            		whoList.show(getSupportFragmentManager(), "wholist");
            	} else {
            		whoList.setArguments(args);
            	}
	            break;
            case NetworkConnection.EVENT_NAMESLIST:
                    event = (IRCCloudJSONObject)msg.obj;
                    args = new Bundle();
                    args.putString("event", event.toString());
                    NamesListFragment namesList = (NamesListFragment)getSupportFragmentManager().findFragmentByTag("nameslist");
                    if(namesList == null) {
                        namesList = new NamesListFragment();
                        namesList.setArguments(args);
                        namesList.show(getSupportFragmentManager(), "nameslist");
                    } else {
                        namesList.setArguments(args);
                    }
                    break;
			case NetworkConnection.EVENT_WHOIS:
				event = (IRCCloudJSONObject)msg.obj;
            	args = new Bundle();
            	args.putString("event", event.toString());
            	WhoisFragment whois = (WhoisFragment)getSupportFragmentManager().findFragmentByTag("whois");
            	if(whois == null) {
            		whois = new WhoisFragment();
            		whois.setArguments(args);
            		whois.show(getSupportFragmentManager(), "whois");
            	} else {
            		whois.setArguments(args);
            	}
	            break;
			case NetworkConnection.EVENT_LISTRESPONSEFETCHING:
				event = (IRCCloudJSONObject)msg.obj;
				String dialogtitle = "List of channels on " + ServersDataSource.getInstance().getServer(event.cid()).hostname;
				if(channelsListDialog == null) {
	            	Context ctx = MessageActivity.this;
	        		if(Build.VERSION.SDK_INT < 11)
	        			ctx = new ContextThemeWrapper(ctx, android.R.style.Theme_Dialog);
	        		AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
	            	builder.setView(getLayoutInflater().inflate(R.layout.dialog_channelslist, null));
	            	builder.setTitle(dialogtitle);
	        		builder.setNegativeButton("Close", new DialogInterface.OnClickListener() {
	        			@Override
	        			public void onClick(DialogInterface dialog, int which) {
	        				dialog.dismiss();
	        			}
	        		});
	        		channelsListDialog = builder.create();
	        		channelsListDialog.setOwnerActivity(MessageActivity.this);
				} else {
					channelsListDialog.setTitle(dialogtitle);
				}
        		channelsListDialog.show();
				ChannelListFragment channels = (ChannelListFragment)getSupportFragmentManager().findFragmentById(R.id.channelListFragment);
            	args = new Bundle();
            	args.putInt("cid", event.cid());
        		channels.setArguments(args);
	            break;
			case NetworkConnection.EVENT_USERINFO:
		    	updateUsersListFragmentVisibility();
				supportInvalidateOptionsMenu();
		        if(refreshUpIndicatorTask != null)
		        	refreshUpIndicatorTask.cancel(true);
		        refreshUpIndicatorTask = new RefreshUpIndicatorTask();
		        refreshUpIndicatorTask.execute((Void)null);
		        if(launchBid == -1 && cid == -1 && conn != null && conn.getUserInfo() != null)
		        	launchBid = conn.getUserInfo().last_selected_bid;
				break;
			case NetworkConnection.EVENT_STATUSCHANGED:
				try {
					event = (IRCCloudJSONObject)msg.obj;
					if(event.cid() == cid) {
						status = event.getString("new_status");
						supportInvalidateOptionsMenu();
					}
				} catch (Exception e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
				break;
			case NetworkConnection.EVENT_MAKESERVER:
				ServersDataSource.Server server = (ServersDataSource.Server)msg.obj;
				if(server.cid == cid) {
					status = server.status;
                    if(type.equalsIgnoreCase("console")) {
                        name = server.name;
                        title.setText(name);
                    }
                    supportInvalidateOptionsMenu();
                    update_subtitle();
				} else {
                    cidToOpen = server.cid;
                    bufferToOpen = "*";
                }
				break;
			case NetworkConnection.EVENT_BUFFERARCHIVED:
				event_bid = (Integer)msg.obj;
				if(event_bid == bid) {
					archived = 1;
					supportInvalidateOptionsMenu();
					subtitle.setVisibility(View.VISIBLE);
					subtitle.setText("(archived)");
				}
				break;
			case NetworkConnection.EVENT_BUFFERUNARCHIVED:
				event_bid = (Integer)msg.obj;
				if(event_bid == bid) {
					archived = 0;
					supportInvalidateOptionsMenu();
					subtitle.setVisibility(View.GONE);
				}
				break;
			case NetworkConnection.EVENT_JOIN:
				event = (IRCCloudJSONObject)msg.obj;
				if(event.bid() == bid && event.type().equalsIgnoreCase("you_joined_channel")) {
					joined = 1;
					supportInvalidateOptionsMenu();
					updateUsersListFragmentVisibility();
				}
				break;
			case NetworkConnection.EVENT_PART:
            case NetworkConnection.EVENT_KICK:
				event = (IRCCloudJSONObject)msg.obj;
				if(event.bid() == bid && event.type().toLowerCase().startsWith("you_")) {
					joined = 0;
					supportInvalidateOptionsMenu();
					updateUsersListFragmentVisibility();
				}
				break;
			case NetworkConnection.EVENT_CHANNELINIT:
				ChannelsDataSource.Channel channel = (ChannelsDataSource.Channel)msg.obj;
				if(channel.bid == bid) {
					joined = 1;
					archived = 0;
			    	update_subtitle();
					supportInvalidateOptionsMenu();
					updateUsersListFragmentVisibility();
				}
				break;
            case NetworkConnection.EVENT_BACKLOG_END:
                if(drawerLayout != null) {
                    drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
                    upView.setVisibility(View.VISIBLE);
                    updateUsersListFragmentVisibility();
                }
                if(cid == -1 || launchURI != null) {
                    if(launchURI == null || !open_uri(launchURI)) {
                        if(launchBid == -1 || !open_bid(launchBid)) {
                            if(conn == null || conn.getUserInfo() == null || !open_bid(conn.getUserInfo().last_selected_bid)) {
                                if(!open_bid(BuffersDataSource.getInstance().firstBid())) {
                                    if(drawerLayout != null && NetworkConnection.getInstance().ready) {
                                        drawerLayout.openDrawer(Gravity.LEFT);
                                    }
                                }
                            }
                        }
                    }
                }
                update_subtitle();
                if(refreshUpIndicatorTask != null)
                    refreshUpIndicatorTask.cancel(true);
                refreshUpIndicatorTask = new RefreshUpIndicatorTask();
                refreshUpIndicatorTask.execute((Void)null);
                if(bid == -1 || BuffersDataSource.getInstance().getBuffer(bid) != null)
                    break;
                msg.obj = new Integer(bid);
                msg.what = NetworkConnection.EVENT_DELETEBUFFER;
			case NetworkConnection.EVENT_CONNECTIONDELETED:
			case NetworkConnection.EVENT_DELETEBUFFER:
				Integer id = (Integer)msg.obj;
				if(msg.what==NetworkConnection.EVENT_DELETEBUFFER) {
					for(int i = 0; i < backStack.size(); i++) {
						if(backStack.get(i).equals(id)) {
							backStack.remove(i);
							i--;
						}
					}
				}
				if(id == ((msg.what==NetworkConnection.EVENT_CONNECTIONDELETED)?cid:bid)) {
                    while(backStack != null && backStack.size() > 0) {
                        Integer bid = backStack.get(0);
                        backStack.remove(0);
                        BuffersDataSource.Buffer buffer = BuffersDataSource.getInstance().getBuffer(bid);
                        if(buffer != null) {
                            ServersDataSource.Server s = ServersDataSource.getInstance().getServer(buffer.cid);
                            if(s != null)
                                status = s.status;
                            String name = buffer.name;
                            if(buffer.type.equalsIgnoreCase("console")) {
                                if(s != null) {
                                    if(s.name != null && s.name.length() > 0)
                                        name = s.name;
                                    else
                                        name = s.hostname;
                                }
                            }
                            onBufferSelected(buffer.cid, buffer.bid, name, buffer.last_seen_eid, buffer.min_eid,
                                    buffer.type, 1, buffer.archived, status);
                            if(backStack.size() > 0)
                                backStack.remove(0);
                            return;
                        }
                    }
		        	if(BuffersDataSource.getInstance().count() == 0) {
                        startActivity(new Intent(MessageActivity.this, EditConnectionActivity.class));
                        finish();
                    } else {
		        		if(!open_bid(BuffersDataSource.getInstance().firstBid()))
                            finish();
		        	}
				}
				break;
            case NetworkConnection.EVENT_CHANNELMODE:
                event = (IRCCloudJSONObject)msg.obj;
                if(event.bid() == bid) {
                    update_subtitle();
                }
                break;
			case NetworkConnection.EVENT_CHANNELTOPIC:
				event = (IRCCloudJSONObject)msg.obj;
				if(event.bid() == bid) {
		        	try {
						if(event.getString("topic").length() > 0) {
							subtitle.setVisibility(View.VISIBLE);
							subtitle.setText(event.getString("topic"));
						} else {
							subtitle.setVisibility(View.GONE);
						}
					} catch (Exception e1) {
						subtitle.setVisibility(View.GONE);
						e1.printStackTrace();
					}
				}
				break;
			case NetworkConnection.EVENT_SELFBACK:
		    	try {
					event = (IRCCloudJSONObject)msg.obj;
					if(event.cid() == cid && event.getString("nick").equalsIgnoreCase(name)) {
			    		subtitle.setVisibility(View.GONE);
						subtitle.setText("");
					}
				} catch (Exception e1) {
					e1.printStackTrace();
				}
				break;
			case NetworkConnection.EVENT_AWAY:
		    	try {
					event = (IRCCloudJSONObject)msg.obj;
					if((event.bid() == bid || (event.type().equalsIgnoreCase("self_away") && event.cid() == cid)) && event.getString("nick").equalsIgnoreCase(name)) {
			    		subtitle.setVisibility(View.VISIBLE);
			    		if(event.has("away_msg"))
			    			subtitle.setText("Away: " + event.getString("away_msg"));
			    		else
			    			subtitle.setText("Away: " + event.getString("msg"));
					}
				} catch (Exception e1) {
					e1.printStackTrace();
				}
				break;
			case NetworkConnection.EVENT_HEARTBEATECHO:
		        if(refreshUpIndicatorTask != null)
		        	refreshUpIndicatorTask.cancel(true);
		        refreshUpIndicatorTask = new RefreshUpIndicatorTask();
		        refreshUpIndicatorTask.execute((Void)null);
				break;
			case NetworkConnection.EVENT_FAILURE_MSG:
				event = (IRCCloudJSONObject)msg.obj;
				if(event.has("_reqid")) {
					int reqid = event.getInt("_reqid");
					if(pendingEvents.containsKey(reqid)) {
						EventsDataSource.Event e = pendingEvents.get(reqid);
						EventsDataSource.getInstance().deleteEvent(e.eid, e.bid);
						pendingEvents.remove(event.getInt("_reqid"));
                        e.failed = true;
                        e.bg_color = R.color.error;
						conn.notifyHandlers(NetworkConnection.EVENT_BUFFERMSG, e);
					}
				} else {
                    if(event.getString("message").equalsIgnoreCase("auth")) {
                        conn.logout();
                        Intent i = new Intent(MessageActivity.this, MainActivity.class);
                        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(i);
                        finish();
                    }
                    if(event.getString("message").equalsIgnoreCase("set_shard")) {
                        NetworkConnection.getInstance().disconnect();
                        NetworkConnection.getInstance().ready = false;
                        SharedPreferences.Editor editor = getSharedPreferences("prefs", 0).edit();
                        editor.putString("session_key", event.getString("cookie"));
                        NetworkConnection.getInstance().connect(event.getString("cookie"));
                        editor.commit();
                    }
                }
				break;
			case NetworkConnection.EVENT_BUFFERMSG:
				try {
					EventsDataSource.Event e = (EventsDataSource.Event)msg.obj;
					if(e.bid != bid && upView != null) {
				        if(refreshUpIndicatorTask != null)
				        	refreshUpIndicatorTask.cancel(true);
				        refreshUpIndicatorTask = new RefreshUpIndicatorTask();
				        refreshUpIndicatorTask.execute((Void)null);
					}
					if(e.from.equalsIgnoreCase(name)) {
						for(EventsDataSource.Event e1 : pendingEvents.values()) {
							EventsDataSource.getInstance().deleteEvent(e1.eid, e1.bid);
						}
						pendingEvents.clear();
					} else if(pendingEvents.containsKey(e.reqid)) {
						e = pendingEvents.get(e.reqid);
						EventsDataSource.getInstance().deleteEvent(e.eid, e.bid);
						pendingEvents.remove(e.reqid);
					}
				} catch (Exception e1) {
				}
				break;
			}
		}
	};
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	if(type != null && NetworkConnection.getInstance().ready) {
	    	if(type.equalsIgnoreCase("channel")) {
	    		getMenuInflater().inflate(R.menu.activity_message_channel_userlist, menu);
	    		getMenuInflater().inflate(R.menu.activity_message_channel, menu);
	    	} else if(type.equalsIgnoreCase("conversation"))
	    		getMenuInflater().inflate(R.menu.activity_message_conversation, menu);
	    	else if(type.equalsIgnoreCase("console"))
	    		getMenuInflater().inflate(R.menu.activity_message_console, menu);
	
	    	getMenuInflater().inflate(R.menu.activity_message_archive, menu);
    	}
    	getMenuInflater().inflate(R.menu.activity_main, menu);

        return super.onCreateOptionsMenu(menu);
    }
    
    @Override
    public boolean onPrepareOptionsMenu (Menu menu) {
    	if(type != null && NetworkConnection.getInstance().ready) {
        	if(archived == 0) {
        		menu.findItem(R.id.menu_archive).setTitle(R.string.menu_archive);
        	} else {
        		menu.findItem(R.id.menu_archive).setTitle(R.string.menu_unarchive);
        	}
	    	if(type.equalsIgnoreCase("channel")) {
	        	if(joined == 0) {
	        		menu.findItem(R.id.menu_leave).setTitle(R.string.menu_rejoin);
	        		menu.findItem(R.id.menu_archive).setVisible(true);
	        		menu.findItem(R.id.menu_archive).setEnabled(true);
	        		menu.findItem(R.id.menu_delete).setVisible(true);
	        		menu.findItem(R.id.menu_delete).setEnabled(true);
	        		if(menu.findItem(R.id.menu_userlist) != null) {
	        			menu.findItem(R.id.menu_userlist).setEnabled(false);
	        			menu.findItem(R.id.menu_userlist).setVisible(false);
	        		}
	        		menu.findItem(R.id.menu_ban_list).setVisible(false);
	        		menu.findItem(R.id.menu_ban_list).setEnabled(false);
	        	} else {
	        		menu.findItem(R.id.menu_leave).setTitle(R.string.menu_leave);
	        		menu.findItem(R.id.menu_archive).setVisible(false);
	        		menu.findItem(R.id.menu_archive).setEnabled(false);
	        		menu.findItem(R.id.menu_delete).setVisible(false);
	        		menu.findItem(R.id.menu_delete).setEnabled(false);
	        		menu.findItem(R.id.menu_ban_list).setVisible(true);
	        		menu.findItem(R.id.menu_ban_list).setEnabled(true);
	        		if(menu.findItem(R.id.menu_userlist) != null) {
		        		boolean hide = false;
		        		try {
		        			if(conn != null && conn.getUserInfo() != null && conn.getUserInfo().prefs != null) {
								JSONObject hiddenMap = conn.getUserInfo().prefs.getJSONObject("channel-hiddenMembers");
								if(hiddenMap.has(String.valueOf(bid)) && hiddenMap.getBoolean(String.valueOf(bid)))
									hide = true;
		        			}
						} catch (JSONException e) {
						}
						if(hide) {
			        		menu.findItem(R.id.menu_userlist).setEnabled(false);
			        		menu.findItem(R.id.menu_userlist).setVisible(false);
						} else {
			        		menu.findItem(R.id.menu_userlist).setEnabled(true);
			        		menu.findItem(R.id.menu_userlist).setVisible(true);
						}
	        		}
	        	}
	    	} else if(type.equalsIgnoreCase("console")) {
	    		menu.findItem(R.id.menu_archive).setVisible(false);
	    		menu.findItem(R.id.menu_archive).setEnabled(false);
	    		if(status != null && (status.equalsIgnoreCase("waiting_to_retry")) || (status.contains("connected") && !status.startsWith("dis"))) {
	    			menu.findItem(R.id.menu_disconnect).setTitle(R.string.menu_disconnect);
	        		menu.findItem(R.id.menu_delete).setVisible(false);
	        		menu.findItem(R.id.menu_delete).setEnabled(false);
	    		} else {
	    			menu.findItem(R.id.menu_disconnect).setTitle(R.string.menu_reconnect);
	        		menu.findItem(R.id.menu_delete).setVisible(true);
	        		menu.findItem(R.id.menu_delete).setEnabled(true);
	    		}
	    	}
    	}
    	return super.onPrepareOptionsMenu(menu);
    }

    private class ToggleListener implements DrawerLayout.DrawerListener  {
        private SlideDrawable mSlider = null;

        @Override
        public void onDrawerSlide(View view, float slideOffset) {
            if(view != null && ((DrawerLayout.LayoutParams)view.getLayoutParams()).gravity == Gravity.LEFT) {
                float glyphOffset = mSlider.getOffset();
                if (slideOffset > 0.5f) {
                    glyphOffset = Math.max(glyphOffset, Math.max(0.f, slideOffset - 0.5f) * 2);
                } else {
                    glyphOffset = Math.min(glyphOffset, slideOffset * 2);
                }
                mSlider.setOffset(glyphOffset);
            }
        }

        public void setUpDrawable(Drawable d) {
            mSlider = new SlideDrawable(d);
            mSlider.setOffsetBy(1.f / 3);
            upView.setImageDrawable(mSlider);
            if(drawerLayout.isDrawerOpen(Gravity.LEFT)) {
                mSlider.setOffset(1.f);
            } else {
                mSlider.setOffset(0.f);
            }
        }

        @Override
        public void onDrawerOpened(View view) {
            if(((DrawerLayout.LayoutParams)view.getLayoutParams()).gravity == Gravity.LEFT) {
                drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, Gravity.RIGHT);
                mSlider.setOffset(1.f);
            } else {
                drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, Gravity.LEFT);
            }
        }

        @Override
        public void onDrawerClosed(View view) {
            if(((DrawerLayout.LayoutParams)view.getLayoutParams()).gravity == Gravity.LEFT) {
                drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
                mSlider.setOffset(0.f);
                updateUsersListFragmentVisibility();
            } else {
                drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, Gravity.LEFT);
            }
        }

        @Override
        public void onDrawerStateChanged(int i) {

        }

        class SlideDrawable extends Drawable implements Drawable.Callback {
            private Drawable mWrapped;
            private float mOffset;
            private float mOffsetBy;

            private final Rect mTmpRect = new Rect();

            public SlideDrawable(Drawable wrapped) {
                mWrapped = wrapped;
            }

            public void setOffset(float offset) {
                mOffset = offset;
                invalidateSelf();
            }

            public float getOffset() {
                return mOffset;
            }

            public void setOffsetBy(float offsetBy) {
                mOffsetBy = offsetBy;
                invalidateSelf();
            }

            @Override
            public void draw(Canvas canvas) {
                mWrapped.copyBounds(mTmpRect);
                canvas.save();
                canvas.translate(mOffsetBy * mTmpRect.width() * -mOffset, 0);
                mWrapped.draw(canvas);
                canvas.restore();
            }

            @Override
            public void setChangingConfigurations(int configs) {
                mWrapped.setChangingConfigurations(configs);
            }

            @Override
            public int getChangingConfigurations() {
                return mWrapped.getChangingConfigurations();
            }

            @Override
            public void setDither(boolean dither) {
                mWrapped.setDither(dither);
            }

            @Override
            public void setFilterBitmap(boolean filter) {
                mWrapped.setFilterBitmap(filter);
            }

            @Override
            public void setAlpha(int alpha) {
                mWrapped.setAlpha(alpha);
            }

            @Override
            public void setColorFilter(ColorFilter cf) {
                mWrapped.setColorFilter(cf);
            }

            @Override
            public void setColorFilter(int color, PorterDuff.Mode mode) {
                mWrapped.setColorFilter(color, mode);
            }

            @Override
            public void clearColorFilter() {
                mWrapped.clearColorFilter();
            }

            @Override
            public boolean isStateful() {
                return mWrapped.isStateful();
            }

            @Override
            public boolean setState(int[] stateSet) {
                return mWrapped.setState(stateSet);
            }

            @Override
            public int[] getState() {
                return mWrapped.getState();
            }

            @Override
            public Drawable getCurrent() {
                return mWrapped.getCurrent();
            }

            @Override
            public boolean setVisible(boolean visible, boolean restart) {
                return super.setVisible(visible, restart);
            }

            @Override
            public int getOpacity() {
                return mWrapped.getOpacity();
            }

            @Override
            public Region getTransparentRegion() {
                return mWrapped.getTransparentRegion();
            }

            @Override
            protected boolean onStateChange(int[] state) {
                mWrapped.setState(state);
                return super.onStateChange(state);
            }

            @Override
            protected void onBoundsChange(Rect bounds) {
                super.onBoundsChange(bounds);
                mWrapped.setBounds(bounds);
            }

            @Override
            public int getIntrinsicWidth() {
                return mWrapped.getIntrinsicWidth();
            }

            @Override
            public int getIntrinsicHeight() {
                return mWrapped.getIntrinsicHeight();
            }

            @Override
            public int getMinimumWidth() {
                return mWrapped.getMinimumWidth();
            }

            @Override
            public int getMinimumHeight() {
                return mWrapped.getMinimumHeight();
            }

            @Override
            public boolean getPadding(Rect padding) {
                return mWrapped.getPadding(padding);
            }

            @Override
            public ConstantState getConstantState() {
                return super.getConstantState();
            }

            @Override
            public void invalidateDrawable(Drawable who) {
                if (who == mWrapped) {
                    invalidateSelf();
                }
            }

            @Override
            public void scheduleDrawable(Drawable who, Runnable what, long when) {
                if (who == mWrapped) {
                    scheduleSelf(what, when);
                }
            }

            @Override
            public void unscheduleDrawable(Drawable who, Runnable what) {
                if (who == mWrapped) {
                    unscheduleSelf(what);
                }
            }
        }
    };

    private ToggleListener mDrawerListener = new ToggleListener();

    private OnClickListener upClickListener = new OnClickListener() {

		@Override
		public void onClick(View arg0) {
        	if(drawerLayout != null) {
	        	if(drawerLayout.isDrawerOpen(Gravity.LEFT)) {
                    drawerLayout.closeDrawers();
	        	} else if(upView.getVisibility() == View.VISIBLE) {
                    if(drawerLayout.isDrawerOpen(Gravity.RIGHT))
                        drawerLayout.closeDrawers();
                    drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, Gravity.LEFT);
                    drawerLayout.openDrawer(Gravity.LEFT);
	        	}
		    	if(!getSharedPreferences("prefs", 0).getBoolean("bufferSwipeTip", false)) {
		    		Toast.makeText(MessageActivity.this, "Drag from the edge of the screen to quickly open and close channels and conversations list", Toast.LENGTH_LONG).show();
		    		SharedPreferences.Editor editor = getSharedPreferences("prefs", 0).edit();
		    		editor.putBoolean("bufferSwipeTip", true);
		    		editor.commit();
		    	}
        	}
		}
    	
    };
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	AlertDialog.Builder builder;
    	AlertDialog dialog;
    	
        switch (item.getItemId()) {
	        case R.id.menu_whois:
	        	conn.whois(cid, name, null);
	        	break;
	        case R.id.menu_identify:
	        	NickservFragment nsFragment = new NickservFragment();
	        	nsFragment.setCid(cid);
	            nsFragment.show(getSupportFragmentManager(), "nickserv");
	            break;
	        case R.id.menu_add_network:
				if(getWindowManager().getDefaultDisplay().getWidth() < 800) {
					Intent i = new Intent(this, EditConnectionActivity.class);
					startActivity(i);
				} else {
		        	EditConnectionFragment connFragment = new EditConnectionFragment();
		            connFragment.show(getSupportFragmentManager(), "addnetwork");
				}
	            break;
	        case R.id.menu_channel_options:
	        	ChannelOptionsFragment newFragment = new ChannelOptionsFragment(cid, bid);
	            newFragment.show(getSupportFragmentManager(), "channeloptions");
	        	break;
	        case R.id.menu_buffer_options:
	        	BufferOptionsFragment bufferFragment = new BufferOptionsFragment(cid, bid, type);
	        	bufferFragment.show(getSupportFragmentManager(), "bufferoptions");
	        	break;
            case R.id.menu_userlist:
            	if(drawerLayout != null) {
		        	if(drawerLayout.isDrawerOpen(Gravity.RIGHT)) {
	        			drawerLayout.closeDrawers();
		        	} else {
                        drawerLayout.closeDrawer(Gravity.LEFT);
                        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, Gravity.RIGHT);
                        drawerLayout.openDrawer(Gravity.RIGHT);
		        	}
		        	upView.setVisibility(View.VISIBLE);
			    	if(!getSharedPreferences("prefs", 0).getBoolean("userSwipeTip", false)) {
			    		Toast.makeText(this, "Drag from the edge of the screen to quickly open and close the user list", Toast.LENGTH_LONG).show();
			    		SharedPreferences.Editor editor = getSharedPreferences("prefs", 0).edit();
			    		editor.putBoolean("userSwipeTip", true);
			    		editor.commit();
			    	}
            	}
            	return true;
            case R.id.menu_ignore_list:
            	Bundle args = new Bundle();
            	args.putInt("cid", cid);
	        	IgnoreListFragment ignoreList = new IgnoreListFragment();
	        	ignoreList.setArguments(args);
	            ignoreList.show(getSupportFragmentManager(), "ignorelist");
                return true;
            case R.id.menu_ban_list:
            	conn.mode(cid, name, "b");
                return true;
            case R.id.menu_leave:
            	if(joined == 0)
            		conn.join(cid, name, null);
            	else
            		conn.part(cid, name, null);
            	return true;
            case R.id.menu_archive:
            	if(archived == 0)
            		conn.archiveBuffer(cid, bid);
            	else
            		conn.unarchiveBuffer(cid, bid);
            	return true;
            case R.id.menu_delete:
            	builder = new AlertDialog.Builder(MessageActivity.this);
            	
            	if(type.equalsIgnoreCase("console"))
            		builder.setTitle("Delete Connection");
            	else
            		builder.setTitle("Delete History");
            	
            	if(type.equalsIgnoreCase("console"))
            		builder.setMessage("Are you sure you want to remove this connection?");
            	else if(type.equalsIgnoreCase("channel"))
            		builder.setMessage("Are you sure you want to clear your history in " + name + "?");
            	else
            		builder.setMessage("Are you sure you want to clear your history with " + name + "?");
            	
            	builder.setPositiveButton("Cancel", new DialogInterface.OnClickListener() {

					@Override
					public void onClick(DialogInterface dialog, int which) {
						dialog.dismiss();
					}
            	});
            	builder.setNeutralButton("Delete", new DialogInterface.OnClickListener() {

					@Override
					public void onClick(DialogInterface dialog, int which) {
		            	if(type.equalsIgnoreCase("console")) {
		            		NetworkConnection.getInstance().deleteServer(cid);
		            	} else {
		                	NetworkConnection.getInstance().deleteBuffer(cid, bid);
		            	}
						dialog.dismiss();
					}
            	});
	    		dialog = builder.create();
	    		dialog.setOwnerActivity(MessageActivity.this);
	    		dialog.show();
            	return true;
            case R.id.menu_editconnection:
				if(getWindowManager().getDefaultDisplay().getWidth() < 800) {
					Intent i = new Intent(this, EditConnectionActivity.class);
					i.putExtra("cid", cid);
					startActivity(i);
				} else {
		        	EditConnectionFragment editFragment = new EditConnectionFragment();
		        	editFragment.setCid(cid);
		            editFragment.show(getSupportFragmentManager(), "editconnection");
				}
            	return true;
            case R.id.menu_disconnect:
                if(status != null && (status.equalsIgnoreCase("waiting_to_retry")) || (status.contains("connected") && !status.startsWith("dis"))) {
        			conn.disconnect(cid, null);
        		} else {
        			conn.reconnect(cid);
        		}
        		return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    void editTopic() {
    	ChannelsDataSource.Channel c = ChannelsDataSource.getInstance().getChannelForBuffer(bid);
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		LayoutInflater inflater = getLayoutInflater();
    	View view = inflater.inflate(R.layout.dialog_textprompt,null);
    	TextView prompt = (TextView)view.findViewById(R.id.prompt);
    	final EditText input = (EditText)view.findViewById(R.id.textInput);
    	input.setText(c.topic_text);
    	prompt.setVisibility(View.GONE);
    	builder.setTitle("Channel Topic");
		builder.setView(view);
		builder.setPositiveButton("Set Topic", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				conn.topic(cid, name, input.getText().toString());
				dialog.dismiss();
			}
		});
		builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				dialog.dismiss();
			}
		});
		AlertDialog dialog = builder.create();
		dialog.setOwnerActivity(this);
		dialog.getWindow().setSoftInputMode (WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
		dialog.show();
    }
    
	@Override
	public void onMessageDoubleClicked(EventsDataSource.Event event) {
		if(event == null)
			return;
		
		String from = event.from;
		if(from == null || from.length() == 0)
			from = event.nick;
		
		onUserDoubleClicked(from);
	}

	@Override
	public void onUserDoubleClicked(String from) {
		if(messageTxt == null || from == null || from.length() == 0)
			return;

    	if(!getSharedPreferences("prefs", 0).getBoolean("mentionTip", false)) {
    		SharedPreferences.Editor editor = getSharedPreferences("prefs", 0).edit();
    		editor.putBoolean("mentionTip", true);
    		editor.commit();
    	}
		
		if(drawerLayout != null)
			drawerLayout.closeDrawers();
		
		if(messageTxt.getText().length() == 0) {
			messageTxt.append(from + ": ");
		} else {
			int oldPosition = messageTxt.getSelectionStart();
			String text = messageTxt.getText().toString();
			int start = oldPosition - 1;
			if(start > 0 && text.charAt(start) == ' ')
				start--;
			while(start > 0 && text.charAt(start) != ' ')
				start--;
			int match = text.indexOf(from, start);
			int end = oldPosition + from.length();
			if(end > text.length() - 1)
				end = text.length() - 1;
			if(match >= 0 && match < end) {
				String newtext = "";
				if(match > 1 && text.charAt(match - 1) == ' ')
					newtext = text.substring(0, match - 1);
				else
					newtext = text.substring(0, match);
				if(match+from.length() < text.length() && text.charAt(match+from.length()) == ':' &&
						match+from.length()+1 < text.length() && text.charAt(match+from.length()+1) == ' ') {
					if(match+from.length()+2 < text.length())
						newtext += text.substring(match+from.length()+2, text.length());
				} else if(match+from.length() < text.length()) {
					newtext += text.substring(match+from.length(), text.length());
				}
				if(newtext.endsWith(" "))
					newtext = newtext.substring(0, newtext.length() - 1);
				if(newtext.equals(":"))
					newtext = "";
				messageTxt.setText(newtext);
				if(match < newtext.length())
					messageTxt.setSelection(match);
				else
					messageTxt.setSelection(newtext.length());
			} else {
				if(oldPosition == text.length() - 1) {
					text += " " + from;
				} else {
					String newtext = text.substring(0, oldPosition);
					if(!newtext.endsWith(" "))
						from = " " + from;
					if(!text.substring(oldPosition, text.length()).startsWith(" "))
						from += " ";
					newtext += from;
					newtext += text.substring(oldPosition, text.length());
					if(newtext.endsWith(" "))
						newtext = newtext.substring(0, newtext.length() - 1);
					text = newtext;
				}
				messageTxt.setText(text);
				if(text.length() > 0) {
					if(oldPosition + from.length() + 2 < text.length())
						messageTxt.setSelection(oldPosition + from.length());
					else
						messageTxt.setSelection(text.length());
				}
			}
		}
		messageTxt.requestFocus();
		InputMethodManager keyboard = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        keyboard.showSoftInput(messageTxt, 0);
	}
	
	@Override
	public boolean onBufferLongClicked(BuffersDataSource.Buffer b) {
   		if(b == null)
			return false;

   		ArrayList<String> itemList = new ArrayList<String>();
   		final String[] items;
   		final BuffersDataSource.Buffer buffer = b;
		ServersDataSource.Server s = ServersDataSource.getInstance().getServer(buffer.cid);

		if(buffer.bid != bid)
			itemList.add("Open");
		
		if(ChannelsDataSource.getInstance().getChannelForBuffer(b.bid) != null) {
			itemList.add("Leave");
			itemList.add("Display Options…");
		} else {
			if(b.type.equalsIgnoreCase("channel"))
				itemList.add("Join");
			else if(b.type.equalsIgnoreCase("console")) {
				if(s.status.equalsIgnoreCase("waiting_to_retry") || (s.status.contains("connected") && !s.status.startsWith("dis"))) {
					itemList.add("Disconnect");
				} else {
					itemList.add("Connect");
					itemList.add("Delete");
				}
				itemList.add("Edit Connection…");
			}
			if(!b.type.equalsIgnoreCase("console")) {
				if(b.archived == 0)
					itemList.add("Archive");
				else
					itemList.add("Unarchive");
				itemList.add("Delete");
			}
			if(!b.type.equalsIgnoreCase("channel")) {
				itemList.add("Display Options…");
			}
		}

		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		if(b.type.equalsIgnoreCase("console"))
			builder.setTitle(s.name);
		else
			builder.setTitle(b.name);
		items = itemList.toArray(new String[itemList.size()]);
		builder.setItems(items, new DialogInterface.OnClickListener() {
		    public void onClick(DialogInterface dialogInterface, int item) {
	    		AlertDialog.Builder builder = new AlertDialog.Builder(MessageActivity.this);
	    		AlertDialog dialog;

	    		if(items[item].equals("Open")) {
	    			ServersDataSource.Server s = ServersDataSource.getInstance().getServer(buffer.cid);
	    			if(buffer.type.equalsIgnoreCase("console")) {
	    				onBufferSelected(buffer.cid, buffer.bid, s.name, buffer.last_seen_eid, buffer.min_eid, 
	    						buffer.type, 1, buffer.archived, s.status);
	    			} else {
		    			onBufferSelected(buffer.cid, buffer.bid, buffer.name, buffer.last_seen_eid, buffer.min_eid, 
		    					buffer.type, 1, buffer.archived, s.status);
	    			}
	    		} else if(items[item].equals("Join")) {
	    			conn.join(buffer.cid, buffer.name, null);
	    		} else if(items[item].equals("Leave")) {
	    			conn.part(buffer.cid, buffer.name, null);
	    		} else if(items[item].equals("Archive")) {
	    			conn.archiveBuffer(buffer.cid, buffer.bid);
	    		} else if(items[item].equals("Unarchive")) {
	    			conn.unarchiveBuffer(buffer.cid, buffer.bid);
	    		} else if(items[item].equals("Connect")) {
	    			conn.reconnect(buffer.cid);
	    		} else if(items[item].equals("Disconnect")) {
	    			conn.disconnect(buffer.cid, null);
	    		} else if(items[item].equals("Display Options…")) {
	    			if(buffer.type.equals("channel")) {
			        	ChannelOptionsFragment newFragment = new ChannelOptionsFragment(buffer.cid, buffer.bid);
			            newFragment.show(getSupportFragmentManager(), "channeloptions");
	    			} else {
			        	BufferOptionsFragment newFragment = new BufferOptionsFragment(buffer.cid, buffer.bid, buffer.type);
			            newFragment.show(getSupportFragmentManager(), "bufferoptions");
	    			}
	    		} else if(items[item].equals("Edit Connection…")) {
		        	EditConnectionFragment editFragment = new EditConnectionFragment();
		        	editFragment.setCid(buffer.cid);
		            editFragment.show(getSupportFragmentManager(), "editconnection");
	    		} else if(items[item].equals("Delete")) {
	            	builder = new AlertDialog.Builder(MessageActivity.this);
	            	
	            	if(buffer.type.equalsIgnoreCase("console"))
	            		builder.setTitle("Delete Connection");
	            	else
	            		builder.setTitle("Delete History");
	            	
	            	if(buffer.type.equalsIgnoreCase("console"))
	            		builder.setMessage("Are you sure you want to remove this connection?");
	            	else if(buffer.type.equalsIgnoreCase("channel"))
	            		builder.setMessage("Are you sure you want to clear your history in " + buffer.name + "?");
	            	else
	            		builder.setMessage("Are you sure you want to clear your history with " + buffer.name + "?");
	            	
	            	builder.setPositiveButton("Cancel", new DialogInterface.OnClickListener() {

						@Override
						public void onClick(DialogInterface dialog, int which) {
							dialog.dismiss();
						}
	            	});
	            	builder.setNeutralButton("Delete", new DialogInterface.OnClickListener() {

						@Override
						public void onClick(DialogInterface dialog, int which) {
			            	if(type.equalsIgnoreCase("console")) {
			            		conn.deleteServer(buffer.cid);
			            	} else {
			                	conn.deleteBuffer(buffer.cid, buffer.bid);
			            	}
							dialog.dismiss();
						}
	            	});
		    		dialog = builder.create();
		    		dialog.setOwnerActivity(MessageActivity.this);
		    		dialog.show();
	    		}
		    }
		});
		
		AlertDialog dialog = builder.create();
		dialog.setOwnerActivity(this);
		dialog.show();
		return true;
	}
	
	@Override
	public boolean onMessageLongClicked(EventsDataSource.Event event) {
		String from = event.from;
		if(from == null || from.length() == 0)
			from = event.nick;

		UsersDataSource.User user;
		
		if(type.equals("channel"))
			user = UsersDataSource.getInstance().getUser(cid, bid, from);
		else
			user = UsersDataSource.getInstance().getUser(cid, from);

		if(user == null && from != null && event.hostmask != null) {
			user = UsersDataSource.getInstance().new User();
			user.nick = from;
			user.hostmask = event.hostmask;
			user.mode = "";
		}
		
		if(user == null && event.html == null)
			return false;
		
		if(event.html != null)
			showUserPopup(user, ColorFormatter.html_to_spanned(event.timestamp + " " + event.html));
		else
			showUserPopup(user, null);
		return true;
    }
    
	@Override
	public void onUserSelected(int c, String chan, String nick) {
		UsersDataSource u = UsersDataSource.getInstance();
		if(type.equals("channel"))
			showUserPopup(u.getUser(cid, bid, nick), null);
		else
			showUserPopup(u.getUser(cid, nick), null);
	}
	
	@SuppressLint("NewApi")
    @SuppressWarnings("deprecation")
	private void showUserPopup(UsersDataSource.User user, Spanned message) {
		ArrayList<String> itemList = new ArrayList<String>();
   		final String[] items;
   		final Spanned text_to_copy = message;
		selected_user = user;
		
		AlertDialog.Builder builder = new AlertDialog.Builder(this);

		if(message != null)
			itemList.add("Copy Message");
		
		if(selected_user != null) {
			itemList.add("Whois…");
			itemList.add("Send a message");
			itemList.add("Mention");
			itemList.add("Invite to a channel…");
			itemList.add("Ignore");
			if(type.equalsIgnoreCase("channel")) {
				UsersDataSource.User self_user = UsersDataSource.getInstance().getUser(cid, bid, ServersDataSource.getInstance().getServer(cid).nick);
				if(self_user != null && self_user.mode != null) {
					if(self_user.mode.contains("q") || self_user.mode.contains("a") || self_user.mode.contains("o")) {
						if(selected_user.mode.contains("o"))
							itemList.add("Deop");
						else
							itemList.add("Op");
					}
					if(self_user.mode.contains("q") || self_user.mode.contains("a") || self_user.mode.contains("o") || self_user.mode.contains("h")) {
						itemList.add("Kick…");
						itemList.add("Ban…");
					}
				}
			}
		}

		items = itemList.toArray(new String[itemList.size()]);
		
		if(selected_user != null)
            if(selected_user.hostmask != null && selected_user.hostmask.length() > 0)
    			builder.setTitle(selected_user.nick + "\n(" + selected_user.hostmask + ")");
            else
                builder.setTitle(selected_user.nick);
		else
			builder.setTitle("Message");
		
		builder.setItems(items, new DialogInterface.OnClickListener() {
		    public void onClick(DialogInterface dialogInterface, int item) {
	    		AlertDialog.Builder builder = new AlertDialog.Builder(MessageActivity.this);
	    		LayoutInflater inflater = getLayoutInflater();
	    		ServersDataSource s = ServersDataSource.getInstance();
	    		ServersDataSource.Server server = s.getServer(cid);
	    		View view;
	    		final TextView prompt;
	    		final EditText input;
	    		AlertDialog dialog;

	    		if(items[item].equals("Copy Message")) {
	    			if(Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.HONEYCOMB) {
						android.text.ClipboardManager clipboard = (android.text.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
	    			    clipboard.setText(text_to_copy);
	    			} else {
	    			    @SuppressLint("ServiceCast") android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
	    			    android.content.ClipData clip = android.content.ClipData.newPlainText("IRCCloud Message",text_to_copy);
	    			    clipboard.setPrimaryClip(clip);
	    			}
	    		} else if(items[item].equals("Whois…")) {
	    			conn.whois(cid, selected_user.nick, null);
	    		} else if(items[item].equals("Send a message")) {
		    		BuffersDataSource b = BuffersDataSource.getInstance();
		    		BuffersDataSource.Buffer buffer = b.getBufferByName(cid, selected_user.nick);
		    		if(getSupportFragmentManager().findFragmentById(R.id.BuffersList) != null) {
			    		if(buffer != null) {
			    			onBufferSelected(buffer.cid, buffer.bid, buffer.name, buffer.last_seen_eid, buffer.min_eid, 
			    					buffer.type, 1, buffer.archived, status);
			    		} else {
			    			onBufferSelected(cid, -1, selected_user.nick, 0, 0, "conversation", 1, 0, status);
			    		}
		    		} else {
			    		Intent i = new Intent(MessageActivity.this, MessageActivity.class);
			    		if(buffer != null) {
				    		i.putExtra("cid", buffer.cid);
				    		i.putExtra("bid", buffer.bid);
				    		i.putExtra("name", buffer.name);
				    		i.putExtra("last_seen_eid", buffer.last_seen_eid);
				    		i.putExtra("min_eid", buffer.min_eid);
				    		i.putExtra("type", buffer.type);
				    		i.putExtra("joined", 1);
				    		i.putExtra("archived", buffer.archived);
				    		i.putExtra("status", status);
			    		} else {
				    		i.putExtra("cid", cid);
				    		i.putExtra("bid", -1);
				    		i.putExtra("name", selected_user.nick);
				    		i.putExtra("last_seen_eid", 0L);
				    		i.putExtra("min_eid", 0L);
				    		i.putExtra("type", "conversation");
				    		i.putExtra("joined", 1);
				    		i.putExtra("archived", 0);
				    		i.putExtra("status", status);
			    		}
			    		startActivity(i);
		    		}
	    		} else if(items[item].equals("Mention")) {
			    	if(!getSharedPreferences("prefs", 0).getBoolean("mentionTip", false)) {
			    		Toast.makeText(MessageActivity.this, "Double-tap a message to quickly reply to the sender", Toast.LENGTH_LONG).show();
			    		SharedPreferences.Editor editor = getSharedPreferences("prefs", 0).edit();
			    		editor.putBoolean("mentionTip", true);
			    		editor.commit();
			    	}
	    			onUserDoubleClicked(selected_user.nick);
	    		} else if(items[item].equals("Invite to a channel…")) {
		        	view = inflater.inflate(R.layout.dialog_textprompt,null);
		        	prompt = (TextView)view.findViewById(R.id.prompt);
		        	input = (EditText)view.findViewById(R.id.textInput);
		        	prompt.setText("Invite " + selected_user.nick + " to a channel");
		        	builder.setTitle(server.name + " (" + server.hostname + ":" + (server.port) + ")");
		    		builder.setView(view);
		    		builder.setPositiveButton("Invite", new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							conn.invite(cid, input.getText().toString(), selected_user.nick);
							dialog.dismiss();
						}
		    		});
		    		builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							dialog.dismiss();
						}
		    		});
		    		dialog = builder.create();
		    		dialog.setOwnerActivity(MessageActivity.this);
		    		dialog.getWindow().setSoftInputMode (WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
		    		dialog.show();
	    		} else if(items[item].equals("Ignore")) {
		        	view = inflater.inflate(R.layout.dialog_textprompt,null);
		        	prompt = (TextView)view.findViewById(R.id.prompt);
		        	input = (EditText)view.findViewById(R.id.textInput);
		        	input.setText("*!"+selected_user.hostmask);
		        	prompt.setText("Ignore messages for " + selected_user.nick + " at this hostmask");
		        	builder.setTitle(server.name + " (" + server.hostname + ":" + (server.port) + ")");
		    		builder.setView(view);
		    		builder.setPositiveButton("Ignore", new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							conn.ignore(cid, input.getText().toString());
							dialog.dismiss();
						}
		    		});
		    		builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							dialog.dismiss();
						}
		    		});
		    		dialog = builder.create();
		    		dialog.setOwnerActivity(MessageActivity.this);
		    		dialog.getWindow().setSoftInputMode (WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
		    		dialog.show();
	    		} else if(items[item].equals("Op")) {
	    			conn.mode(cid, name, "+o " + selected_user.nick);
	    		} else if(items[item].equals("Deop")) {
	    			conn.mode(cid, name, "-o " + selected_user.nick);
	    		} else if(items[item].equals("Kick…")) {
		        	view = inflater.inflate(R.layout.dialog_textprompt,null);
		        	prompt = (TextView)view.findViewById(R.id.prompt);
		        	input = (EditText)view.findViewById(R.id.textInput);
		        	prompt.setText("Give a reason for kicking");
		        	builder.setTitle(server.name + " (" + server.hostname + ":" + (server.port) + ")");
		    		builder.setView(view);
		    		builder.setPositiveButton("Kick", new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							conn.kick(cid, name, selected_user.nick, input.getText().toString());
							dialog.dismiss();
						}
		    		});
		    		builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							dialog.dismiss();
						}
		    		});
		    		dialog = builder.create();
		    		dialog.setOwnerActivity(MessageActivity.this);
		    		dialog.getWindow().setSoftInputMode (WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
		    		dialog.show();
	    		} else if(items[item].equals("Ban…")) {
		        	view = inflater.inflate(R.layout.dialog_textprompt,null);
		        	prompt = (TextView)view.findViewById(R.id.prompt);
		        	input = (EditText)view.findViewById(R.id.textInput);
		        	input.setText("*!"+selected_user.hostmask);
		        	prompt.setText("Add a banmask for " + selected_user.nick);
		        	builder.setTitle(server.name + " (" + server.hostname + ":" + (server.port) + ")");
		    		builder.setView(view);
		    		builder.setPositiveButton("Ban", new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							conn.mode(cid, name, "+b " + input.getText().toString());
							dialog.dismiss();
						}
		    		});
		    		builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							dialog.dismiss();
						}
		    		});
		    		dialog = builder.create();
		    		dialog.setOwnerActivity(MessageActivity.this);
		    		dialog.getWindow().setSoftInputMode (WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
		    		dialog.show();
	    		}
		    	dialogInterface.dismiss();
		    }
		});
		
		AlertDialog dialog = builder.create();
		dialog.setOwnerActivity(this);
		dialog.show();
    }

	@Override
	public void onBufferSelected(int cid, int bid, String name,
			long last_seen_eid, long min_eid, String type, int joined,
			int archived, String status) {
		if(drawerLayout != null) {
            drawerLayout.closeDrawers();
			upView.setVisibility(View.VISIBLE);
		}
		if(bid != this.bid || this.cid == -1) {
			if(bid != -1 && conn != null && conn.getUserInfo() != null) {
				conn.getUserInfo().last_selected_bid = bid;
            }
	    	for(int i = 0; i < backStack.size(); i++) {
	    		if(backStack.get(i) == this.bid)
	    			backStack.remove(i);
	    	}
	    	if(this.bid >= 0) {
	    		backStack.add(0, this.bid);
                BuffersDataSource.getInstance().updateDraft(this.bid, messageTxt.getText().toString());
            }
            if(this.bid == -1 || this.cid == -1)
                shouldFadeIn = false;
            else
                shouldFadeIn = true;
            this.cid = cid;
			this.bid = bid;
			this.name = name;
			this.type = type;
			this.joined = joined;
			this.archived = archived;
			this.status = status;
	    	title.setText(name);
	    	getSupportActionBar().setTitle(name);
	    	update_subtitle();
	    	final Bundle b = new Bundle();
	    	b.putInt("cid", cid);
	    	b.putInt("bid", bid);
	    	b.putLong("last_seen_eid", last_seen_eid);
	    	b.putLong("min_eid", min_eid);
	    	b.putString("name", name);
	    	b.putString("type", type);
	    	BuffersListFragment blf = (BuffersListFragment)getSupportFragmentManager().findFragmentById(R.id.BuffersList);
	    	final MessageViewFragment mvf = (MessageViewFragment)getSupportFragmentManager().findFragmentById(R.id.messageViewFragment);
	    	UsersListFragment ulf = (UsersListFragment)getSupportFragmentManager().findFragmentById(R.id.usersListFragment);
            if(mvf != null)
                mvf.ready = false;
	    	if(blf != null)
	    		blf.setSelectedBid(bid);
	    	if(ulf != null)
	    		ulf.setArguments(b);

            if(shouldFadeIn) {
                AlphaAnimation anim = new AlphaAnimation(1, 0);
                anim.setDuration(150);
                anim.setFillAfter(true);
                anim.setAnimationListener(new Animation.AnimationListener() {
                    @Override
                    public void onAnimationStart(Animation animation) {

                    }

                    @Override
                    public void onAnimationEnd(Animation animation) {
                        if(mvf != null)
                            mvf.setArguments(b);
                        messageTxt.setText("");
                        String draft = BuffersDataSource.getInstance().draftForBuffer(MessageActivity.this.bid);
                        if(draft != null)
                            messageTxt.append(draft);
                    }

                    @Override
                    public void onAnimationRepeat(Animation animation) {

                    }
                });
                try {
                    mvf.showSpinner(true);
                    mvf.getListView().startAnimation(anim);
                    ulf.getListView().startAnimation(anim);
                } catch (Exception e) {

                }
            } else {
                if(mvf != null)
                    mvf.setArguments(b);
                messageTxt.setText("");
                String draft = BuffersDataSource.getInstance().draftForBuffer(MessageActivity.this.bid);
                if(draft != null)
                    messageTxt.append(draft);
            }

	    	updateUsersListFragmentVisibility();
	    	supportInvalidateOptionsMenu();
			if(showNotificationsTask != null)
				showNotificationsTask.cancel(true);
			showNotificationsTask = new ShowNotificationsTask();
			showNotificationsTask.execute(bid);
	    	if(upView != null)
	    		new RefreshUpIndicatorTask().execute((Void)null);
		}
		if(cid != -1) {
			if(drawerLayout != null)
				drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, Gravity.LEFT);
		}
	}

	@Override
	public void onMessageViewReady() {
		if(shouldFadeIn) {
	    	MessageViewFragment mvf = (MessageViewFragment)getSupportFragmentManager().findFragmentById(R.id.messageViewFragment);
	    	UsersListFragment ulf = (UsersListFragment)getSupportFragmentManager().findFragmentById(R.id.usersListFragment);
	    	AlphaAnimation anim = new AlphaAnimation(0, 1);
			anim.setDuration(150);
			anim.setFillAfter(true);
			if(mvf != null && mvf.getListView() != null) {
                mvf.showSpinner(false);
				mvf.getListView().startAnimation(anim);
            }
			if(ulf != null && ulf.getListView() != null)
				ulf.getListView().startAnimation(anim);
			shouldFadeIn = false;
        }
	}

	@Override
	public void addButtonPressed(int cid) {
		if(drawerLayout != null)
			drawerLayout.openDrawer(Gravity.LEFT);
	}


    private boolean checkPlayServices() {
        int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
        if (resultCode != ConnectionResult.SUCCESS) {
            if (GooglePlayServicesUtil.isUserRecoverableError(resultCode)) {
                GooglePlayServicesUtil.getErrorDialog(resultCode, this, 9000).show();
            }
            return false;
        }
        return true;
    }
}
