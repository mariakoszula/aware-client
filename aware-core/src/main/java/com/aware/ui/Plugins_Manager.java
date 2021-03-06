
package com.aware.ui;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.widget.GridLayout;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.aware.Aware;
import com.aware.R;
import com.aware.providers.Aware_Provider.Aware_Plugins;
import com.aware.utils.Aware_Plugin;
import com.aware.utils.Https;

import org.apache.http.HttpResponse;
import org.apache.http.ParseException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

/**
 * UI to manage installed plugins. 
 * @author denzil
 *
 */
public class Plugins_Manager extends Aware_Activity {
    
	/**
	 * Plugin installed but disabled
	 */
	public static final int PLUGIN_DISABLED = 0;
	/**
	 * Plugin installed and active
	 */
	public static final int PLUGIN_ACTIVE = 1;
	/**
	 * Plugin installed but there is a new version on the server
	 */
	public static final int PLUGIN_UPDATED = 2;
	/**
	 * Plugin not installed but available on the server
	 */
	public static final int PLUGIN_NOT_INSTALLED = 3;
	
	private static LayoutInflater inflater;
	private static GridLayout store_grid;
	private static ProgressBar loading_plugins;
	
	private static boolean is_refreshing = false;
	
	@Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.plugins_store_ui);

    	inflater = getLayoutInflater();
    	store_grid = (GridLayout) findViewById(R.id.plugins_store_grid);
    	loading_plugins = (ProgressBar) findViewById(R.id.loading_plugins);
    	
    	IntentFilter filter = new IntentFilter();
    	filter.addAction(Aware.ACTION_AWARE_PLUGIN_MANAGER_REFRESH);
    	registerReceiver(plugins_listener, filter);
    }
	
	//Monitors for external changes in plugin's states and refresh the UI
	private Plugins_Listener plugins_listener = new Plugins_Listener();
	public class Plugins_Listener extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			if( intent.getAction().equals(Aware.ACTION_AWARE_PLUGIN_MANAGER_REFRESH) ) {
				if( ! is_refreshing ) {
					new Async_PluginUpdater().execute();
				}
			}
		}
	}
	
	private AlertDialog.Builder getPluginInfoDialog( String name, String version, String description, String developer ) {
		AlertDialog.Builder builder = new AlertDialog.Builder( Plugins_Manager.this );
		View plugin_info_view = inflater.inflate(R.layout.plugins_store_pkg_detail, null);
		TextView plugin_name = (TextView) plugin_info_view.findViewById(R.id.plugin_name);
		TextView plugin_version = (TextView) plugin_info_view.findViewById(R.id.plugin_version);
		TextView plugin_description = (TextView) plugin_info_view.findViewById(R.id.plugin_description);
		TextView plugin_developer = (TextView) plugin_info_view.findViewById(R.id.plugin_developer);
		
		plugin_name.setText(name);
		plugin_version.setText("Version: " + version);
		plugin_description.setText(description);
		plugin_developer.setText("Developer: " + developer);
		builder.setView(plugin_info_view);
		
		return builder;
	}
	
	@Override
	protected void onResume() {
		super.onResume();
        drawUI();
		if( ! is_refreshing ) {
			new Async_PluginUpdater().execute();
		}
	}
    
    @Override
    protected void onDestroy() {
    	super.onDestroy();
    	unregisterReceiver(plugins_listener);
    }
    
    /**
	 * Downloads and compresses image for optimized icon caching
	 * @param image_url
	 * @return
	 */
	public static byte[] cacheImage( String image_url, Context sContext ) {
		try {
			CertificateFactory cf = CertificateFactory.getInstance("X.509");
			InputStream caInput = sContext.getResources().openRawResource(R.raw.aware);
			Certificate ca;
			try {
				ca = cf.generateCertificate(caInput);
			} finally {
				caInput.close();
			}
			
			KeyStore sKeyStore = KeyStore.getInstance(KeyStore.getDefaultType());
			InputStream inStream = sContext.getResources().openRawResource(R.raw.awareframework);
			sKeyStore.load(inStream, "awareframework".toCharArray());
			inStream.close();
			
			sKeyStore.setCertificateEntry("ca", ca);
			
			String tmfAlgorithm = TrustManagerFactory.getDefaultAlgorithm();
			TrustManagerFactory tmf = TrustManagerFactory.getInstance(tmfAlgorithm);
			tmf.init(sKeyStore);
			
			SSLContext context = SSLContext.getInstance("TLS");
			context.init(null, tmf.getTrustManagers(), null);
			
			//Fetch image now that we recognise SSL
			URL image_path = new URL(image_url.replace("http://", "https://")); //make sure we are fetching the images over https
			HttpsURLConnection image_connection = (HttpsURLConnection) image_path.openConnection();
			image_connection.setSSLSocketFactory(context.getSocketFactory());
			
			InputStream in_stream = image_connection.getInputStream();
			Bitmap tmpBitmap = BitmapFactory.decodeStream(in_stream);
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			tmpBitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
			
			return output.toByteArray();
			
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (KeyStoreException e) {
			e.printStackTrace();
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		} catch (CertificateException e) {
			e.printStackTrace();
		} catch (KeyManagementException e) {
			e.printStackTrace();
		} catch (NullPointerException e) {
            e.printStackTrace();
        }
		return null;
	}
    
	/**
	 * Given a package and class name, check if the class exists or not.
	 * @param package_name
	 * @param class_name
	 * @return boolean
	 */
	private boolean isClassAvailable( String package_name, String class_name ) {
		try{
			Context package_context = createPackageContext(package_name, Context.CONTEXT_IGNORE_SECURITY | Context.CONTEXT_INCLUDE_CODE); 
			package_context.getClassLoader().loadClass(package_name+"."+class_name);			
		} catch ( ClassNotFoundException e ) {
			return false;
		} catch ( NameNotFoundException e ) {
			return false;
		}
		return true;
	}

    /**
     * Checks if a plugin is installed on the device
     * @param context
     * @param package_name
     * @return
     */
    public static boolean isInstalled( Context context, String package_name ) {
        PackageManager pkgManager = context.getPackageManager();
        List<PackageInfo> packages = pkgManager.getInstalledPackages(PackageManager.GET_META_DATA);
        for( PackageInfo pkg : packages) {
            if( pkg.packageName.equals(package_name) ) return true;
        }
        return false;
    }

    /**
     * Returns the currently installed plugin's version
     * @param context
     * @param package_name
     * @return
     */
    public static int getVersion( Context context, String package_name ) {
        try {
            PackageInfo pkgInfo = context.getPackageManager().getPackageInfo(package_name, PackageManager.GET_META_DATA);
            return pkgInfo.versionCode;
        } catch (NameNotFoundException e) {
            if( Aware.DEBUG ) Log.d( Aware.TAG, e.getMessage());
        }
        return 0;
    }
	
	private void drawUI() {
		//Clear previous states
		store_grid.removeAllViews();

		//Build UI
		Cursor installed_plugins = getContentResolver().query(Aware_Plugins.CONTENT_URI, null, null, null, Aware_Plugins.PLUGIN_NAME + " ASC");
        if( installed_plugins != null && installed_plugins.moveToFirst()) {
            do{
    			final String package_name = installed_plugins.getString(installed_plugins.getColumnIndex(Aware_Plugins.PLUGIN_PACKAGE_NAME));
    			final String name = installed_plugins.getString(installed_plugins.getColumnIndex(Aware_Plugins.PLUGIN_NAME));
    			final String description = installed_plugins.getString(installed_plugins.getColumnIndex(Aware_Plugins.PLUGIN_DESCRIPTION));
    			final String developer = installed_plugins.getString(installed_plugins.getColumnIndex(Aware_Plugins.PLUGIN_AUTHOR));
    			final String version = installed_plugins.getString(installed_plugins.getColumnIndex(Aware_Plugins.PLUGIN_VERSION));
    			final int status = installed_plugins.getInt(installed_plugins.getColumnIndex(Aware_Plugins.PLUGIN_STATUS));
    			
    			final View pkg_view = inflater.inflate(R.layout.plugins_store_pkg_list_item, null, false);

    			try {
    				ImageView pkg_icon = (ImageView) pkg_view.findViewById(R.id.pkg_icon);
    				if( status != PLUGIN_NOT_INSTALLED ) {
    					ApplicationInfo appInfo = getPackageManager().getApplicationInfo(package_name, PackageManager.GET_META_DATA);
    					pkg_icon.setImageDrawable(appInfo.loadIcon(getPackageManager()));
    				} else {
    					byte[] img = installed_plugins.getBlob(installed_plugins.getColumnIndex(Aware_Plugins.PLUGIN_ICON));
                        if( img != null && img.length > 0 ) pkg_icon.setImageBitmap(BitmapFactory.decodeByteArray(img, 0, img.length));
    				}
    				
    				TextView pkg_title = (TextView) pkg_view.findViewById(R.id.pkg_title);
    				pkg_title.setText(installed_plugins.getString(installed_plugins.getColumnIndex(Aware_Plugins.PLUGIN_NAME)));
    				
    				ImageView pkg_state = (ImageView) pkg_view.findViewById(R.id.pkg_state);
    				
    				switch(status) {
    					case PLUGIN_DISABLED:
    						pkg_state.setVisibility(View.INVISIBLE);
    						pkg_view.setOnClickListener( new View.OnClickListener() {
    							@Override
    							public void onClick(View v) {
    								AlertDialog.Builder builder = getPluginInfoDialog(name, version, description, developer);
    								if( isClassAvailable(package_name, "Settings") ) {
        								builder.setNegativeButton("Settings", new DialogInterface.OnClickListener() {
    										@Override
    										public void onClick(DialogInterface dialog, int which) {
    											dialog.dismiss();
    											Intent open_settings = new Intent();
    		    								open_settings.setClassName(package_name, package_name + ".Settings");
    		    								startActivity(open_settings);
    										}
    									});
    								}
    								builder.setPositiveButton("Activate", new DialogInterface.OnClickListener() {
    									@Override
    									public void onClick(DialogInterface dialog, int which) {
    										dialog.dismiss();
    										Aware.startPlugin(getApplicationContext(), package_name);
    										drawUI();
    									}
    								});
    								builder.create().show();
    							}});
    						break;
    					case PLUGIN_ACTIVE:
    						pkg_state.setImageResource(R.drawable.ic_pkg_active);
    						pkg_view.setOnClickListener(new View.OnClickListener() {
    							@Override
    							public void onClick(View v) {
    								AlertDialog.Builder builder = getPluginInfoDialog(name, version, description, developer);
    								if( isClassAvailable(package_name,"Settings") ) {
        								builder.setNegativeButton("Settings", new DialogInterface.OnClickListener() {
    										@Override
    										public void onClick(DialogInterface dialog, int which) {
    											dialog.dismiss();
    											Intent open_settings = new Intent();
    		    								open_settings.setClassName(package_name, package_name + ".Settings");
    		    								startActivity(open_settings);
    										}
    									});
    								}
    								builder.setPositiveButton("Deactivate", new DialogInterface.OnClickListener() {
    									@Override
    									public void onClick(DialogInterface dialog, int which) {
    										dialog.dismiss();
    										Aware.stopPlugin(getApplicationContext(), package_name);
    										drawUI();
    									}
    								});
    								builder.create().show();
    							}});
    						break;
    					case PLUGIN_UPDATED:
    						pkg_state.setImageResource(R.drawable.ic_pkg_updated);
    						pkg_view.setOnClickListener(new View.OnClickListener(){
    							@Override
    							public void onClick(View v) {
    								AlertDialog.Builder builder = getPluginInfoDialog( name, version, description, developer );
    								if( isClassAvailable(package_name, "Settings") ) {
	    								builder.setNegativeButton("Settings", new DialogInterface.OnClickListener() {
											@Override
											public void onClick(DialogInterface dialog, int which) {
												dialog.dismiss();
												Intent open_settings = new Intent();
			    								open_settings.setClassName(package_name, package_name + ".Settings");
			    								startActivity(open_settings);
											}
										});
    								}
    								builder.setNeutralButton("Deactivate", new DialogInterface.OnClickListener() {
										@Override
										public void onClick(DialogInterface dialog, int which) {
											dialog.dismiss();
											Aware.stopPlugin(getApplicationContext(), package_name);
											drawUI();
										}
									});
    								builder.setPositiveButton("Update", new DialogInterface.OnClickListener() {
										@Override
										public void onClick(DialogInterface dialog, int which) {
											dialog.dismiss();
											Aware.downloadPlugin( getApplicationContext(), package_name, true );
										}
									});
    								builder.create().show();
    							}
                            });
    						break;
    					case PLUGIN_NOT_INSTALLED:
    						pkg_state.setImageResource(R.drawable.ic_pkg_download);
    						pkg_view.setOnClickListener(new View.OnClickListener(){
    							@Override
    							public void onClick(View v) {
    								AlertDialog.Builder builder = getPluginInfoDialog( name, version, description, developer );
    								builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
    									@Override
    									public void onClick(DialogInterface dialog, int which) {
    										dialog.dismiss();
    									}
    								});
    								builder.setPositiveButton("Install", new DialogInterface.OnClickListener() {
    									@Override
    									public void onClick(DialogInterface dialog, int which) {
    										dialog.dismiss();
    										Aware.downloadPlugin( getApplicationContext(), package_name, false);
    									}
    								});
    								builder.create().show();
    							}
                            });
    						break;
    				}
    				store_grid.addView(pkg_view);
    			} catch (NameNotFoundException e) {
    				e.printStackTrace();
    			}
    		} while(installed_plugins.moveToNext());
    	}
    	if( installed_plugins != null && ! installed_plugins.isClosed() ) installed_plugins.close();
	}
	
    /**
     * Checks for changes on the server side and updates database.
     * If changes were detected, result is true and a refresh of UI is requested.
     * @author denzil
     */
    public class Async_PluginUpdater extends AsyncTask<Void, View, Void> {
		@Override
    	protected void onPreExecute() {
    		super.onPreExecute();
    		if( loading_plugins != null ) {
    			is_refreshing = true;
        		loading_plugins.setVisibility(View.VISIBLE);
    		}
    	}

        private boolean is_onRepo(JSONArray server_packages, String package_name) {
            for(int i = 0; i < server_packages.length(); i++) {
                try {
                    JSONObject plugin = server_packages.getJSONObject(i);
                    if( plugin.getString("package").equals(package_name) ) return true;
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            return false;
        }

    	@Override
		protected Void doInBackground(Void... params) {
    		//Check for updates on the server side
    		HttpResponse response = new Https(getApplicationContext()).dataGET("https://api.awareframework.com/index.php/plugins/get_plugins" + (( Aware.getSetting(getApplicationContext(), "study_id").length() > 0 ) ? "/" + Aware.getSetting(getApplicationContext(), "study_id") : ""), true );
			if( response != null && response.getStatusLine().getStatusCode() == 200 ) {
				try {
					JSONArray plugins = new JSONArray(Https.undoGZIP(response));

                    //Clean-up
                    Cursor all_plugins = getContentResolver().query(Aware_Plugins.CONTENT_URI, null, null, null, Aware_Plugins.PLUGIN_NAME + " ASC");
                    String to_clean = "";
                    if( all_plugins != null && all_plugins.moveToFirst() ) {
                        do {
                            String package_name = all_plugins.getString(all_plugins.getColumnIndex(Aware_Plugins.PLUGIN_PACKAGE_NAME));
                            if( ! Plugins_Manager.isInstalled(getApplicationContext(), package_name) && ! is_onRepo(plugins, package_name)) {
                                to_clean += package_name + ",";
                            }
                        }while(all_plugins.moveToNext());
                    }
                    if( all_plugins != null && ! all_plugins.isClosed()) all_plugins.close();

                    if( to_clean.length() > 0 ) {
                        to_clean = to_clean.substring(0,to_clean.length()-1);
                        getContentResolver().delete(Aware_Plugins.CONTENT_URI, Aware_Plugins.PLUGIN_PACKAGE_NAME + " in (" + to_clean + ")", null);
                    }

					for( int i=0; i< plugins.length(); i++ ) {
						JSONObject plugin = plugins.getJSONObject(i);

                        boolean new_data = false;
                        Cursor is_cached = getContentResolver().query(Aware_Plugins.CONTENT_URI, null, Aware_Plugins.PLUGIN_PACKAGE_NAME + " LIKE '" + plugin.getString("package") + "'", null, null );
						if( is_cached != null && is_cached.moveToFirst() ) {
							if( ! Plugins_Manager.isInstalled(getApplicationContext(), plugin.getString("package")) ) {
                                //We used to have it installed, now we don't, remove from database
                                getContentResolver().delete(Aware_Plugins.CONTENT_URI, Aware_Plugins.PLUGIN_PACKAGE_NAME + " LIKE '" + plugin.getString("package") + "'", null);
                                new_data = true;
                            } else {
                                int version = is_cached.getInt(is_cached.getColumnIndex(Aware_Plugins.PLUGIN_VERSION));
                                //Lets check if it is updated
                                if( plugin.getInt("version") > version ) {
                                    ContentValues data = new ContentValues();
                                    data.put(Aware_Plugins.PLUGIN_DESCRIPTION, plugin.getString("desc"));
                                    data.put(Aware_Plugins.PLUGIN_AUTHOR, plugin.getString("first_name") + " " + plugin.getString("last_name") + " - " + plugin.getString("email"));
                                    data.put(Aware_Plugins.PLUGIN_NAME, plugin.getString("title"));
                                    data.put(Aware_Plugins.PLUGIN_ICON, cacheImage("http://api.awareframework.com" + plugin.getString("iconpath"), getApplicationContext()));
                                    data.put(Aware_Plugins.PLUGIN_STATUS, PLUGIN_UPDATED);
                                    getContentResolver().update(Aware_Plugins.CONTENT_URI, data, Aware_Plugins._ID + "=" + is_cached.getInt(is_cached.getColumnIndex(Aware_Plugins._ID)), null);
                                }
                            }
						} else {
                            new_data = true;
                        }
                        if( is_cached != null && ! is_cached.isClosed() ) is_cached.close();

                        if( new_data ) {
                            //this is a new plugin available on the server that we don't have yet!
                            ContentValues data = new ContentValues();
                            data.put(Aware_Plugins.PLUGIN_NAME, plugin.getString("title"));
                            data.put(Aware_Plugins.PLUGIN_DESCRIPTION, plugin.getString("desc"));
                            data.put(Aware_Plugins.PLUGIN_VERSION, plugin.getInt("version"));
                            data.put(Aware_Plugins.PLUGIN_PACKAGE_NAME, plugin.getString("package"));
                            data.put(Aware_Plugins.PLUGIN_AUTHOR, plugin.getString("first_name") + " " + plugin.getString("last_name") + " - " + plugin.getString("email"));
                            data.put(Aware_Plugins.PLUGIN_STATUS, PLUGIN_NOT_INSTALLED);
                            data.put(Aware_Plugins.PLUGIN_ICON, cacheImage("http://api.awareframework.com" + plugin.getString("iconpath"), getApplicationContext()));
                            getContentResolver().insert(Aware_Plugins.CONTENT_URI, data);
                        }
					}
				} catch (ParseException e) {
					e.printStackTrace();
				} catch (JSONException e) {
					e.printStackTrace();
				}
			}
			return null;
		}
    	
		@Override
		protected void onPostExecute(Void result) {
			super.onPostExecute(result);
			if( loading_plugins != null ) {
				loading_plugins.setVisibility(View.GONE);
				is_refreshing = false;
				drawUI();
			}
		}
    }
}
