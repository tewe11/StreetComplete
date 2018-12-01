package de.westnordost.streetcomplete.tangram;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PointF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.AnyThread;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.preference.PreferenceManager;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import com.mapzen.android.lost.api.LocationListener;
import com.mapzen.android.lost.api.LocationRequest;
import com.mapzen.android.lost.api.LocationServices;
import com.mapzen.android.lost.api.LostApiClient;
import com.mapzen.tangram.CameraPosition;
import com.mapzen.tangram.CameraUpdateFactory;
import com.mapzen.tangram.LngLat;
import com.mapzen.tangram.MapChangeListener;
import com.mapzen.tangram.MapController;
import com.mapzen.tangram.MapView;
import com.mapzen.tangram.Marker;
import com.mapzen.tangram.SceneError;
import com.mapzen.tangram.TouchInput;
import com.mapzen.tangram.networking.HttpHandler;

import java.io.File;
import java.util.Objects;

import de.westnordost.osmapi.map.data.LatLon;
import de.westnordost.streetcomplete.ApplicationConstants;
import de.westnordost.streetcomplete.Prefs;
import de.westnordost.streetcomplete.R;
import de.westnordost.streetcomplete.util.BitmapUtil;
import de.westnordost.streetcomplete.util.DpUtil;
import de.westnordost.streetcomplete.util.SphericalEarthMath;
import de.westnordost.streetcomplete.util.ViewUtils;

import static android.content.Context.SENSOR_SERVICE;

public class MapFragment extends Fragment
{
	private final CompassComponent compass;
	private boolean isShowingDirection;
	private boolean isCompassMode;

	private LostApiClient lostApiClient;
	private LocationListener locationListener = this::onLocationChanged;
	private Location lastLocation;
	private boolean zoomedYet;
	private boolean isFollowingPosition;

	private CameraPosition cameraPosition = new CameraPosition();
	private CameraPosition lastCameraPosition = new CameraPosition();

	private Marker locationMarker;
	private Marker accuracyMarker;
	private Marker directionMarker;
	private String[] directionMarkerSize;

	private MapView mapView;
	// since it is loaded asynchronously, could still be null still at any point
	@Nullable protected MapController controller;

	private MapControlsFragment mapControls;

	private Listener listener;
	public interface Listener
	{
		@AnyThread void onMapOrientation(float rotation, float tilt);
	}

	public MapFragment()
	{
		compass = new CompassComponent();
		compass.setListener(this::onCompassRotationChanged);
	}

	@Override public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
									   Bundle savedInstanceState)
	{
		View view = inflater.inflate(R.layout.fragment_map, container, false);
		controller = null;
		mapView = view.findViewById(R.id.map);
		return view;
	}

	@Override public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState)
	{
		super.onViewCreated(view, savedInstanceState);
		if(savedInstanceState == null)
		{
			getChildFragmentManager().beginTransaction().add(R.id.controls_fragment, new MapControlsFragment()).commit();
		}
	}

	/* --------------------------------- Map initialization ------------------------------------- */

	public void loadMap(String apiKey)
	{
		loadMap(apiKey, "map_theme/scene.yaml");
	}

	public void loadMap(String apiKey, @NonNull final String sceneFilePath)
	{
		mapView.getMapAsync(mapController -> {
			controller = mapController;
			onMapControllerReady(sceneFilePath);
		}, createHttpHandler(apiKey));
	}

	private HttpHandler createHttpHandler(String apiKey)
	{
		int cacheSize = PreferenceManager.getDefaultSharedPreferences(getContext()).getInt(Prefs.MAP_TILECACHE, 50);

		File cacheDir = getContext().getExternalCacheDir();
		if (cacheDir != null && cacheDir.exists())
		{
			return new TileHttpHandler(ApplicationConstants.USER_AGENT, apiKey, new File(cacheDir, "tile_cache"), cacheSize * 1024L * 1024L);
		}
		return new TileHttpHandler(ApplicationConstants.USER_AGENT, apiKey);
	}

	@CallSuper protected void onMapControllerReady(@NonNull final String sceneFilePath)
	{
		TouchInput touchInput = controller.getTouchInput();

		touchInput.setRotateResponder(new TouchInput.RotateResponder()
		{
			private boolean forward;
			private TouchInput.RotateResponder rotateResponder = controller.getRotateResponder();

			@Override public boolean onRotateBegin()
			{
				forward = requestUnglueViewFromRotation();
				return forward ? rotateResponder.onRotateBegin() : false;
			}

			@Override public boolean onRotate(float x, float y, float rotation)
			{
				return forward ? rotateResponder.onRotate(x,y,rotation) : false;
			}

			@Override public boolean onRotateEnd()
			{
				return forward ? rotateResponder.onRotateEnd() : false;
			}
		});
		touchInput.setPanResponder(new TouchInput.PanResponder()
		{
			private boolean forward;
			private TouchInput.PanResponder panResponder = controller.getPanResponder();

			@Override public boolean onPanBegin()
			{
				forward = requestUnglueViewFromPosition();
				return forward ? panResponder.onPanBegin() : false;
			}

			@Override public boolean onPan(float startX, float startY, float endX, float endY)
			{
				return forward ? panResponder.onPan(startX, startY, endX, endY) : false;
			}

			@Override public boolean onPanEnd()
			{
				return forward ? panResponder.onPanEnd() : false;
			}

			@Override
			public boolean onFling(float posX, float posY, float velocityX, float velocityY)
			{
				forward = requestUnglueViewFromPosition();
				return forward ? panResponder.onFling(posX, posY, velocityX, velocityY) : false;
			}

			@Override public boolean onCancelFling()
			{
				forward = requestUnglueViewFromPosition();
				return forward ? panResponder.onCancelFling() : false;
			}
		});
		touchInput.setDoubleTapResponder(this::onDoubleTap);

		controller.setSceneLoadListener(this::onSceneReady);
		controller.setMapChangeListener(new MapChangeListener()
		{
			@Override public void onViewComplete() { updateView(); }
			@Override public void onRegionWillChange(boolean animated) {}
			@Override public void onRegionIsChanging() { updateView(); }
			@Override public void onRegionDidChange(boolean animated) { }
		});

		restoreMapState();
		tryInitializeMapControls();
		loadScene(sceneFilePath);
	}

	protected void loadScene(String sceneFilePath)
	{
		controller.loadSceneFile(sceneFilePath);
	}

	public void onMapControlsCreated(MapControlsFragment mapControls)
	{
		this.mapControls = mapControls;
		tryInitializeMapControls();
	}

	private void tryInitializeMapControls()
	{
		if(controller != null && mapControls != null)
		{
			mapControls.onMapInitialized();
			onMapOrientation();
		}
	}

	@CallSuper protected void onSceneReady(int sceneId, SceneError sceneError)
	{
		if(getActivity() != null)
		{
			initMarkers();
			showLocation();
			ViewUtils.postOnLayout(getView(), this::updateView);
		}
	}

	private void initMarkers()
	{
		locationMarker = createLocationMarker(3);
		directionMarker = createDirectionMarker(2);
		accuracyMarker = createAccuracyMarker(1);
	}

	private Marker createLocationMarker(int order)
	{
		Marker marker = controller.addMarker();
		BitmapDrawable dot = BitmapUtil.createBitmapDrawableFrom(getResources(), R.drawable.location_dot);
		marker.setStylingFromString("{ style: 'points', color: 'white', size: ["+TextUtils.join(",",sizeInDp(dot))+"], order: 2000, flat: true, collide: false }");
		marker.setDrawable(dot);
		marker.setDrawOrder(order);
		return marker;
	}

	private Marker createDirectionMarker(int order)
	{
		BitmapDrawable directionImg = BitmapUtil.createBitmapDrawableFrom(getResources(), R.drawable.location_direction);
		directionMarkerSize = sizeInDp(directionImg);
		Marker marker = controller.addMarker();
		marker.setDrawable(directionImg);
		marker.setDrawOrder(order);
		return marker;
	}

	private Marker createAccuracyMarker(int order)
	{
		Marker marker = controller.addMarker();
		marker.setDrawable(BitmapUtil.createBitmapDrawableFrom(getResources(), R.drawable.accuracy_circle));
		marker.setDrawOrder(order);
		return marker;
	}

	private String[] sizeInDp(Drawable drawable)
	{
		Context ctx = getContext();
		return new String[]{
			// CSS "px" are in fact density dependent pixels
			DpUtil.toDp(drawable.getIntrinsicWidth(), ctx) + "px",
			DpUtil.toDp(drawable.getIntrinsicHeight(),ctx) + "px"};
	}

	/* ----------------------------------- Responders ------------------------------------------ */

	private boolean onDoubleTap(float x, float y)
	{
		if(controller == null) return true;

		if(requestUnglueViewFromPosition())
		{
			LngLat zoomTo = controller.screenPositionToLngLat(new PointF(x, y));
			CameraPosition position = controller.getCameraPosition(cameraPosition);
			if(zoomTo != null)
			{
				position.longitude = zoomTo.longitude;
				position.latitude = zoomTo.latitude;
			}
			position.zoom += 1;
			controller.updateCameraPosition(CameraUpdateFactory.newCameraPosition(position), 500);
		}
		else
		{
			controller.updateCameraPosition(CameraUpdateFactory.zoomIn(), 500);
		}
		return true;
	}

	protected void updateView()
	{
		if(controller == null) return;

		CameraPosition position = controller.getCameraPosition(cameraPosition);
		if(position.zoom != lastCameraPosition.zoom)
		{
			updateAccuracy();
		}
		if(position.rotation != lastCameraPosition.rotation || position.tilt != lastCameraPosition.tilt)
		{
			onMapOrientation();
		}

		lastCameraPosition.set(cameraPosition);
	}

	private boolean requestUnglueViewFromPosition()
	{
		if(isFollowingPosition)
		{
			if(mapControls == null || mapControls.requestUnglueViewFromPosition())
			{
				setIsFollowingPosition(false);
				setCompassMode(false);
				return true;
			}
			return false;
		}
		return true;
	}

	private boolean requestUnglueViewFromRotation()
	{
		if(isCompassMode)
		{
			if(mapControls == null || mapControls.requestUnglueViewFromRotation())
			{
				setCompassMode(false);
				return true;
			}
			return false;
		}
		return true;
	}

	/* ------------------------------- Location and compass ------------------------------------- */

	private void onLostConnected() throws SecurityException
	{
		zoomedYet = false;
		lastLocation = null;

		LocationRequest request = LocationRequest.create()
			.setInterval(2000)
			.setSmallestDisplacement(5)
			.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

		LocationServices.FusedLocationApi.requestLocationUpdates(lostApiClient, request, locationListener);
	}

	private void onLocationChanged(Location location)
	{
		lastLocation = location;
		compass.setLocation(location);
		showLocation();
		followPosition();
	}

	private void showLocation()
	{
		if(controller != null && lastLocation != null)
		{
			LngLat pos = new LngLat(lastLocation.getLongitude(), lastLocation.getLatitude());
			if(locationMarker != null)
			{
				locationMarker.setVisible(true);
				locationMarker.setPointEased(pos, 1000, MapController.EaseType.CUBIC);
			}
			if(accuracyMarker != null)
			{
				accuracyMarker.setVisible(true);
				accuracyMarker.setPointEased(pos, 1000, MapController.EaseType.CUBIC);
			}
			if(directionMarker != null)
			{
				directionMarker.setVisible(isShowingDirection);
				directionMarker.setPointEased(pos, 1000, MapController.EaseType.CUBIC);
			}

			updateAccuracy();
		}
	}

	private void followPosition()
	{
		if(controller != null && shouldCenterCurrentPosition())
		{
			LngLat pos = new LngLat(lastLocation.getLongitude(), lastLocation.getLatitude());
			if (zoomedYet)
			{
				controller.updateCameraPosition(CameraUpdateFactory.setPosition(pos), 500);
			}
			else
			{
				zoomedYet = true;
				controller.updateCameraPosition(CameraUpdateFactory.newLngLatZoom(pos,19),500);
			}
		}
	}

	protected boolean shouldCenterCurrentPosition()
	{
		return isFollowingPosition && lastLocation != null;
	}

	private void updateAccuracy()
	{
		if(controller != null && lastLocation != null && accuracyMarker != null && accuracyMarker.isVisible())
		{
			LngLat pos = new LngLat(lastLocation.getLongitude(), lastLocation.getLatitude());
			double size = meters2Pixels(controller, pos, lastLocation.getAccuracy());
			accuracyMarker.setStylingFromString("{ style: 'points', color: 'white', size: [" + size + "px, " + size + "px], order: 2000, flat: true, collide: false }");
		}
	}

	private double meters2Pixels(@NonNull MapController controller, LngLat at, float meters) {
		LatLon pos0 = TangramConst.toLatLon(at);
		PointF screenPos0, screenPos1;
		CameraPosition position;
		synchronized (controller)
		{
			position = controller.getCameraPosition(cameraPosition);
			LatLon pos1 = SphericalEarthMath.translate(pos0, meters, -position.rotation);
			screenPos0 = controller.lngLatToScreenPosition(at);
			screenPos1 = controller.lngLatToScreenPosition(TangramConst.toLngLat(pos1));
		}
		return Math.sqrt(Math.pow(screenPos1.y - screenPos0.y,2) +Math.pow(screenPos1.x - screenPos0.x,2));
	}

	private void onMapOrientation()
	{
		CameraPosition position = controller.getCameraPosition(cameraPosition);

		if(mapControls != null) mapControls.onMapOrientation(position.rotation, position.tilt);
		listener.onMapOrientation(position.rotation, position.tilt);
	}

	public void setIsFollowingPosition(boolean value)
	{
		isFollowingPosition = value;
		followPosition();
	}

	public boolean isFollowingPosition()
	{
		return isFollowingPosition;
	}

	public void startPositionTracking()
	{
		if(!lostApiClient.isConnected()) lostApiClient.connect();
	}

	public void stopPositionTracking()
	{
		if(locationMarker != null) locationMarker.setVisible(false);
		if(accuracyMarker != null) accuracyMarker.setVisible(false);
		if(directionMarker != null) directionMarker.setVisible(false);

		lastLocation = null;
		zoomedYet = false;
		isShowingDirection = false;

		if(lostApiClient.isConnected())
		{
			LocationServices.FusedLocationApi.removeLocationUpdates(lostApiClient, locationListener);
		}
		lostApiClient.disconnect();
	}



	/* -------------------------------------- Compass ------------------------------------------- */

	@AnyThread private void onCompassRotationChanged(float rotation, float tilt)
	{
		// we received an event from the compass, so compass is working - direction can be displayed on screen
		isShowingDirection = true;

		if(directionMarker != null)
		{
			if(!directionMarker.isVisible()) directionMarker.setVisible(true);
			double r = rotation * 180 / Math.PI;
			directionMarker.setStylingFromString(
					"{ style: 'points', color: '#cc536dfe', size: [" +
							TextUtils.join(",", directionMarkerSize) +
							"], order: 2000, collide: false, flat: true, angle: " + r + " }");
		}

		if (isCompassMode && controller != null)
		{
			float mapRotation = -rotation;
			controller.updateCameraPosition(CameraUpdateFactory.setRotation(mapRotation));
		}
	}

	public boolean isShowingDirection() { return isShowingDirection; }

	public boolean isCompassMode()
	{
		return isCompassMode;
	}

	public void setCompassMode(boolean isCompassMode)
	{
		this.isCompassMode = isCompassMode;
		if(isCompassMode && controller != null)
		{
			controller.updateCameraPosition(CameraUpdateFactory.setTilt((float) (Math.PI / 5)));
		}
	}



	/* ------------------------------ Save/restore map state ------------------------------------ */

	private static final String
			PREF_ROTATION = "map_rotation",
			PREF_TILT = "map_tilt",
			PREF_ZOOM = "map_zoom",
			PREF_LAT = "map_lat",
			PREF_LON = "map_lon",
			PREF_FOLLOWING = "map_following",
			PREF_COMPASS_MODE = "map_compass_mode";

	private void restoreMapState()
	{
		if(getActivity() == null || controller == null) return;

		SharedPreferences prefs = getActivity().getPreferences(Activity.MODE_PRIVATE);

		CameraPosition position = controller.getCameraPosition(cameraPosition);
		if(prefs.contains(PREF_ROTATION)) position.rotation = prefs.getFloat(PREF_ROTATION,0);
		if(prefs.contains(PREF_TILT)) position.tilt = prefs.getFloat(PREF_TILT,0);
		if(prefs.contains(PREF_ZOOM)) position.zoom = prefs.getFloat(PREF_ZOOM,0);
		if(prefs.contains(PREF_LAT) && prefs.contains(PREF_LON))
		{
			position.latitude = Double.longBitsToDouble(prefs.getLong(PREF_LAT,0));
			position.longitude = Double.longBitsToDouble(prefs.getLong(PREF_LON,0));
		}
		controller.updateCameraPosition(CameraUpdateFactory.newCameraPosition(position));

		setIsFollowingPosition(prefs.getBoolean(PREF_FOLLOWING, true));
		setCompassMode(prefs.getBoolean(PREF_COMPASS_MODE, false));
	}

	private void saveMapState()
	{
		if(getActivity() == null || controller == null) return;

		SharedPreferences.Editor editor = getActivity().getPreferences(Activity.MODE_PRIVATE).edit();
		CameraPosition position = controller.getCameraPosition(cameraPosition);

		editor.putFloat(PREF_ROTATION, position.rotation);
		editor.putFloat(PREF_TILT, position.tilt);
		editor.putFloat(PREF_ZOOM, position.zoom);

		editor.putLong(PREF_LAT, Double.doubleToRawLongBits(position.latitude));
		editor.putLong(PREF_LON, Double.doubleToRawLongBits(position.longitude));
		editor.putBoolean(PREF_FOLLOWING, isFollowingPosition);
		editor.putBoolean(PREF_COMPASS_MODE, isCompassMode);
		editor.apply();
	}



	/* ------------------------------------ Lifecycle ------------------------------------------- */

	@Override public void onCreate(@Nullable Bundle bundle)
	{
		super.onCreate(bundle);
		if(mapView != null) mapView.onCreate(bundle);
	}

	@Override public void onAttach(Context context)
	{
		super.onAttach(context);
		compass.onCreate(
				(SensorManager) context.getSystemService(SENSOR_SERVICE),
				((WindowManager) Objects.requireNonNull(context.getSystemService(Context.WINDOW_SERVICE))).getDefaultDisplay());
		LostApiClient.ConnectionCallbacks callbacks = new LostApiClient.ConnectionCallbacks()
		{
			@Override public void onConnected() { onLostConnected(); }
			@Override public void onConnectionSuspended() {}
		};
		lostApiClient = new LostApiClient.Builder(context).addConnectionCallbacks(callbacks).build();
		listener = (Listener) context;
	}

	@Override public void onResume()
	{
		super.onResume();
		compass.onResume();
		if(mapView != null) mapView.onResume();
	}

	@Override public void onPause()
	{
		super.onPause();
		compass.onPause();
		if(mapView != null) mapView.onPause();
		saveMapState();
	}

	@Override public void onStop()
	{
		super.onStop();
		stopPositionTracking();
	}

	@Override public void onDestroy()
	{
		super.onDestroy();
		compass.onDestroy();
		if(mapView != null) mapView.onDestroy();
		controller = null;
		directionMarker = null;
		accuracyMarker = null;
		locationMarker = null;
	}

	@Override public void onLowMemory()
	{
		super.onLowMemory();
		if(mapView != null) mapView.onLowMemory();
	}



	/* ------------------------ Public interface for map manipulation --------------------------- */

	public void zoomIn()
	{
		if(controller != null) controller.updateCameraPosition(CameraUpdateFactory.zoomIn(), 500);
	}

	public void zoomOut()
	{
		if(controller != null) controller.updateCameraPosition(CameraUpdateFactory.zoomOut(), 500);
	}

	@Nullable public Location getDisplayedLocation()
	{
		return lastLocation;
	}

	public void setMapOrientation(float rotation, float tilt)
	{
		if(controller == null) return;
		CameraPosition position = controller.getCameraPosition(cameraPosition);
		position.rotation = rotation;
		position.tilt = tilt;
		controller.updateCameraPosition(CameraUpdateFactory.newCameraPosition(position));
	}

	public float getRotation()
	{
		return controller != null ? controller.getCameraPosition(cameraPosition).rotation : 0;
	}

	public float getZoom()
	{
		return controller != null ? controller.getCameraPosition(cameraPosition).zoom : 15;
	}

	public LatLon getPosition()
	{
		return controller != null ? TangramConst.toLatLon(controller.getCameraPosition(cameraPosition).getPosition()) : null;
	}

	@Nullable public PointF getPointOf(@NonNull LatLon pos)
	{
		return controller != null ? controller.lngLatToScreenPosition(TangramConst.toLngLat(pos)) : null;
	}

	@Nullable public LatLon getPositionAt(@NonNull PointF pointF)
	{
		if(controller == null) return null;
		LngLat pos = controller.screenPositionToLngLat(pointF);
		if(pos == null) return null;
		return TangramConst.toLatLon(pos);
	}

	public void showMapControls()
	{
		if(mapControls != null) mapControls.showControls();
	}

	public void hideMapControls()
	{
		if(mapControls != null) mapControls.hideControls();
	}
}
