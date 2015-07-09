package de.tum.in.tumcampus.tumonline;

import android.content.Context;
import android.os.AsyncTask;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import de.tum.in.tumcampus.R;
import de.tum.in.tumcampus.auxiliary.NetUtils;
import de.tum.in.tumcampus.auxiliary.Utils;
import de.tum.in.tumcampus.auxiliary.XMLParser;
import de.tum.in.tumcampus.auxiliary.calendar.Event;
import de.tum.in.tumcampus.models.Geo;
import de.tum.in.tumcampus.models.managers.CacheManager;

/**
 * Base class for communication with TUMRoomFinder
 */
public class TUMRoomFinderRequest {

    // Json keys
    public static final String KEY_ARCHITECT_NUMBER = "architect_number";
    public static final String KEY_MAP_ID = "Id";
    public static final String KEY_TITLE = "title";
    public static final String KEY_ROOM_API_CODE = "room_api_code";
    public static final String KEY_CAMPUS_ID = "campusId";
    public static final String KEY_CAMPUS_TITLE = "campusTitle";
    public static final String KEY_BUILDING_TITLE = "buildingTitle";
    public static final String KEY_ROOM_TITLE = "roomTitle";
    public static final String KEY_BUILDING_ID = "buildingId";
    public static final String KEY_UTM_ZONE = "utm_zone";
    public static final String KEY_UTM_EASTING = "utm_easting";
    public static final String KEY_UTM_NORTHING = "utm_northing";

    /**
     * asynchronous task for interactive fetch
     */
    private AsyncTask<String, Void, ArrayList<HashMap<String, String>>> backgroundTask = null;

    /**
     * method to call
     */
    private String method = null;
    /**
     * a list/map for the needed parameters
     */
    private final Map<String, String> parameters;
    private final String SERVICE_BASE_URL = "http://vmbaumgarten3.informatik.tu-muenchen.de/";
    private NetUtils net;

    public TUMRoomFinderRequest(Context context) {
        parameters = new HashMap<>();
        method = "search";
        net = new NetUtils(context);
    }

    public void cancelRequest(boolean mayInterruptIfRunning) {
        // Cancel background task just if one has been established
        if (backgroundTask != null) {
            backgroundTask.cancel(mayInterruptIfRunning);
        }
    }

    /**
     * fetches the room coordinates
     *
     * @param archId architecture id
     * @return coordinates of the room
     */
    public Geo fetchCoordinates(String archId) {

        String url = "http://portal.dev/Api/roomfinder/room/coordinates/" + archId;

        try {
            JSONObject jsonObject = net.downloadJson(url);
            double zone = jsonObject.getDouble(KEY_UTM_ZONE);
            double easting = jsonObject.getDouble(KEY_UTM_EASTING);
            double northing = jsonObject.getDouble(KEY_UTM_NORTHING);

            return UTMtoLL(northing, easting, zone);

        } catch (JSONException e) {
            Utils.log(String.valueOf(e));
        } catch (IOException e) {
            Utils.log(String.valueOf(e));
        }

        // if something went wrong
        return null;
    }

    /**
     * fetches all rooms that match the search string
     *
     * @param searchString string that was entered by the user
     * @return list of HashMaps representing rooms, Map: attributes -> values
     */
    public ArrayList<HashMap<String, String>> fetchRooms(String searchString) {

        ArrayList<HashMap<String, String>> roomsList = new ArrayList<>();
        String url = "http://portal.dev/Api/roomfinder/room/search/" + searchString;
        JSONArray jsonArray = net.downloadJsonArray(url, CacheManager.VALIDITY_DO_NOT_CACHE, true);

        if (jsonArray == null) {
            return null;
        }

        try {
            // TODO: remove all 'undefined' values when backend is completed
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject obj = jsonArray.getJSONObject(i);
                HashMap<String, String> roomMap = new HashMap<>();
                roomMap.put(KEY_CAMPUS_ID, "undefined");
                roomMap.put(KEY_CAMPUS_TITLE, "undefined");
                roomMap.put(KEY_BUILDING_TITLE, obj.getString("address"));
                roomMap.put(KEY_ROOM_TITLE, obj.getString("info"));
                roomMap.put(KEY_BUILDING_ID, obj.getString("unit_id"));
                roomMap.put(KEY_ARCHITECT_NUMBER, obj.getString("arch_id"));
                roomMap.put(KEY_ROOM_API_CODE, obj.getString("room_id"));

                // adding HashList to ArrayList
                roomsList.add(roomMap);
            }
        } catch (JSONException e){
            Utils.log(String.valueOf(e));
        }

        return roomsList;
    }

    /**
     * returns the url to get the default map
     *
     * @param archId architecture id
     * @return url of default map
     */
    public String fetchDefaultMap(String archId) {
        return "http://portal.dev/Api/roomfinder/room/defaultMap/" + archId;
    }



    public ArrayList<HashMap<String, String>> fetchAvailableMaps(String archId) {

        ArrayList<HashMap<String, String>> mapsList = new ArrayList<>();
        String url = "http://portal.dev/Api/roomfinder/room/availableMaps/" + archId;

        JSONArray jsonArray = net.downloadJsonArray(url, CacheManager.VALIDITY_DO_NOT_CACHE, true);

        if (jsonArray == null) {
            return null;
        }

        try {
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject obj = jsonArray.getJSONObject(i);
                HashMap<String, String> mapMap = new HashMap<>();
                mapMap.put(KEY_MAP_ID, obj.getString("map_id"));
                mapMap.put(KEY_TITLE, obj.getString("description"));

                // adding HashList to ArrayList
                mapsList.add(mapMap);
            }
        } catch (JSONException e) {
            Utils.log(String.valueOf(e));
        }

        return mapsList;
    }

    /**
     * fetches the room schedule for a given room e.g. 62015 = Interims HS 2
     * @param roomApiCode rooms api code
     * @return List of Events
     */
    public ArrayList<Event> fetchRoomSchedule(String roomApiCode, String startDate, String endDate, ArrayList<Event> scheduleList) {
        setParameter("start_date", startDate);
        setParameter("end_date", endDate);
        method = roomApiCode;

        String ROOM_SERVICE_DEFAULT_MAP_URL = SERVICE_BASE_URL + "schedule/room/";
        String url = getRequestURL(ROOM_SERVICE_DEFAULT_MAP_URL);
        Utils.log("fetching Map URL " + url);

        try {

            XMLParser parser = new XMLParser();
            String xml = parser.getXmlFromUrl(url); // getting XML from URL
            Document doc = parser.getDomElement(xml); // getting DOM element

            NodeList scheduleNodes = doc.getElementsByTagName("event");

            for (int k = 0; k < scheduleNodes.getLength(); k++) {
                Element schedule = (Element) scheduleNodes.item(k);
                Event event = Event.newInstance();
                event.id = Long.parseLong(parser.getValue(schedule, "eventID"));
                event.title = parser.getValue(schedule, "title");
                String start = parser.getValue(schedule, "begin_time");
                String end = parser.getValue(schedule, "end_time");
                event.setStart(Utils.getISODateTime(start));
                event.setEnd(Utils.getISODateTime(end));
                event.color = Event.getDisplayColorFromColor(0xff28921f);
                scheduleList.add(event);
            }
        } catch (Exception e) {
            Utils.log(e, "FetchError");
            // return e.getMessage();
        }
        return scheduleList;
    }

    /**
     * this fetch method will fetch the data from the TUMRoomFinder Request and
     * will address the listeners onFetch if the fetch succeeded, else the
     * onFetchError will be called
     *
     * @param context      the current context (may provide the current activity)
     * @param listener     the listener, which takes the result
     * @param searchString Text to search for
     */
    public void fetchSearchInteractive(final Context context,
                                       final TUMRoomFinderRequestFetchListener listener,
                                       String searchString) {

        // fetch information in a background task and show progress dialog in
        // meantime
        backgroundTask = new AsyncTask<String, Void, ArrayList<HashMap<String, String>>>() {

            /** property to determine if there is an internet connection */
            boolean isOnline;

            @Override
            protected ArrayList<HashMap<String, String>> doInBackground(
                    String... searchString) {
                // set parameter on the TUMRoomFinder request an fetch the
                // results
                isOnline = NetUtils.isConnected(context);
                if (!isOnline) {
                    // not online, fetch does not make sense
                    return null;
                }
                // we are online, return fetch result

                return fetchRooms(searchString[0]);
            }

            @Override
            protected void onPostExecute(ArrayList<HashMap<String, String>> result) {
                // handle result
                if (!isOnline) {
                    listener.onNoInternetError();
                    return;
                }
                if (result == null) {
                    listener.onFetchError(context
                            .getString(R.string.empty_result));
                    return;
                }
                // If there could not be found any problems return usual on
                // Fetch method
                listener.onFetch(result);
            }

        };

        backgroundTask.execute(searchString);
    }

    /**
     * This will return the URL to the TUMRoomFinderRequest with regard to the
     * set parameters
     *
     * @return a String URL
     */
    String getRequestURL(String baseURL) {
        String url = baseURL + method + "?";
        for (Entry<String, String> pairs : parameters.entrySet()) {
            url += pairs.getKey() + "=" + pairs.getValue() + "&";
        }
        return url;
    }

    /**
     * Sets one parameter name to its given value and deletes all others
     *
     * @param name  identifier of the parameter
     * @param value value of the parameter
     */
    void setParameter(String name, String value) {
        parameters.clear();
        try {
            parameters.put(name, URLEncoder.encode(value, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            Utils.log(e);
        }
    }

    /**
     * Converts UTM based coordinates to latitude and longitude based format
     */
    private Geo UTMtoLL(double north, double east, double zone) {
        double d = 0.99960000000000004;
        double d1 = 6378137;
        double d2 = 0.0066943799999999998;
        double d4 = (1 - Math.sqrt(1 - d2)) / (1 + Math.sqrt(1 - d2));
        double d15 = east - 500000;
        double d11 = ((zone - 1) * 6 - 180) + 3;
        double d3 = d2 / (1 - d2);
        double d10 = north / d;
        double d12 = d10 / (d1 * (1 - d2 / 4 - (3 * d2 * d2) / 64 - (5 * Math.pow(d2, 3)) / 256));
        double d14 = d12 + ((3 * d4) / 2 - (27 * Math.pow(d4, 3)) / 32) * Math.sin(2 * d12) + ((21 * d4 * d4) / 16 - (55 * Math.pow(d4, 4)) / 32) * Math.sin(4 * d12) + ((151 * Math.pow(d4, 3)) / 96) * Math.sin(6 * d12);
        double d5 = d1 / Math.sqrt(1 - d2 * Math.sin(d14) * Math.sin(d14));
        double d6 = Math.tan(d14) * Math.tan(d14);
        double d7 = d3 * Math.cos(d14) * Math.cos(d14);
        double d8 = (d1 * (1 - d2)) / Math.pow(1 - d2 * Math.sin(d14) * Math.sin(d14), 1.5);
        double d9 = d15 / (d5 * d);
        double d17 = d14 - ((d5 * Math.tan(d14)) / d8) * (((d9 * d9) / 2 - (((5 + 3 * d6 + 10 * d7) - 4 * d7 * d7 - 9 * d3) * Math.pow(d9, 4)) / 24) + (((61 + 90 * d6 + 298 * d7 + 45 * d6 * d6) - 252 * d3 - 3 * d7 * d7) * Math.pow(d9, 6)) / 720);
        d17 = d17 * 180 / Math.PI;
        double d18 = ((d9 - ((1 + 2 * d6 + d7) * Math.pow(d9, 3)) / 6) + (((((5 - 2 * d7) + 28 * d6) - 3 * d7 * d7) + 8 * d3 + 24 * d6 * d6) * Math.pow(d9, 5)) / 120) / Math.cos(d14);
        d18 = d11 + d18 * 180 / Math.PI;
        return new Geo(d17, d18);
    }
}
