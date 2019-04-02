package io.github.patpatchpatrick.mmacalendar;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.provider.CalendarContract;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;


public class MainActivity extends AppCompatActivity {

    private TextView webData;
    private Long defaultCalendarID = (long) 1;
    private SharedPreferences prefs;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button loadFightsButton = (Button) findViewById(R.id.loadFightsButton);
        webData = (TextView) findViewById(R.id.textView);
        prefs = PreferenceManager.getDefaultSharedPreferences(this);


        loadFightsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                //Scrape the MMAFighting.com website for fight data, and load it into the user's calendar
                loadFights("ufc");
                loadFights("bellator");

            }
        });
    }

    private void loadFights(final String eventType) {

        //Build the URL that should be loaded depending on the type of event (i.e. UFC, Bellator,etc...)
        StringBuilder mmaURLBuilder = new StringBuilder("https://www.mmafighting.com/schedule/");
        mmaURLBuilder.append(eventType);
        final String mmaURL = mmaURLBuilder.toString();


        new Thread(new Runnable() {
            @Override
            public void run() {

                //StringBuilder to display when fights are finished loading
                final StringBuilder builder = new StringBuilder();

                //List of MMA Events parsed from webpage
                final ArrayList<mmaEvent> mmaEvents = new ArrayList<>();

                try {

                    //Load the web document and associated links (a[href] HTML links and h3 headers) using Jsoup Library
                    //All the fights and event data is stored  in the a[href] links
                    //All the fight dates are stored in the HTML h3 tag headers

                    Document doc = Jsoup.connect(mmaURL).get();
                    Elements links = doc.select("a[href]");
                    Elements htags = doc.select("h3, a[href]");

                    Boolean loadingFights = false;

                    for (Element link : htags) {

                        if (link.text().indexOf("UFC ") != -1 || link.text().indexOf("Bellator ") != -1 || link.text().indexOf("Bellator:") != -1) {
                            //If the text contains UFC or Bellator then it is an event title
                            //A new event is created and all the data in the successive links will be stored in this
                            //MMA event until a new MMA event is loaded
                            loadingFights = true;
                            builder.append("\n").append("Event: ").append(link.text());
                            mmaEvents.add(new mmaEvent(link.text()));
                        } else if (loadingFights) {
                            if (link.text().indexOf("UFC ") != -1 || link.text().indexOf("Bellator ") != -1 || link.text().indexOf("Bellator:") != -1) {
                                //Since fights are being loaded and the text indicates a new UFC/Bellator event
                                //A new MMA event is created
                                builder.append("\n").append("Event: ").append(link.text());
                                mmaEvents.add(new mmaEvent(link.text()));
                            } else if (link.text().indexOf("MMA Fighting") != -1) {
                                //MMA Fighting text displays on the HTML page when there are no more fights to be queried
                                //At this point, the FOR loop can be broken
                                break;
                            } else if (link.tagName() == "h3") {
                                //If the link is an H3 tag, it contains a date, so add the date to the most
                                //current MMA event in the arrayList
                                mmaEvents.get(mmaEvents.size() - 1).addDate(link.text().trim());
                            } else {
                                //If the link doesn't meet any of the above criteria, then it is a fight
                                //Add the fight data to the most current MMA event in the arrayList
                                mmaEvents.get(mmaEvents.size() - 1).addFight(link.text());
                            }
                        }


                    }


                } catch (IOException e) {
                    //If there is an IOException, let the user know there was an error loading the fights
                    builder.append("Error loading fights");
                    Log.d("Error", e.getMessage());
                }

                //After the fights are loaded, back on the UI thread, create/update calendar events for the fights
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        webData.append("\n\n" + eventType.toUpperCase() + " Events Loaded Into Calendar: \n" + builder.toString());
                        checkDefaultCalendar();
                        for (mmaEvent event : mmaEvents) {
                            insertOrUpdateCalendarEvent(event);
                        }
                    }
                });

            }
        }).start();


    }

    private void insertOrUpdateCalendarEvent(mmaEvent event) {

        if(event.date != null){

            //Get the calendar event ID for the event name (will be -1 if there was no calendar event created)
            Long eventID = prefs.getLong(event.name, (long) -1);

            if(eventID != (long) -1){
                //If the event has a long ID associated with it's name in the shared preferences, then
                //the event has previously been inserted into the calendar.  This event should be updated.
                updateEvent(event, eventID);
            } else  {
                //If the event does not have a long ID associated with it's name in the shared preferences, then
                //the event has not ever been inserted into the calendar and should be inserted
                insertEvent(event);
            }
        }

    }

    private void insertEvent(mmaEvent event){

        //Insert a new event into the Calendar

        long startMillis = 0;
        long endMillis = 0;
        Calendar beginTime = Calendar.getInstance();
        beginTime.set(event.date.getYear()+1900, event.date.getMonth(), event.date.getDate(), 17, 00);
        startMillis = beginTime.getTimeInMillis();
        Calendar endTime = Calendar.getInstance();
        endTime.set(event.date.getYear()+1900, event.date.getMonth(), event.date.getDate(), 20, 30);
        endMillis = endTime.getTimeInMillis();

        ContentResolver cr = getContentResolver();
        ContentValues values = new ContentValues();
        values.put(CalendarContract.Events.DTSTART, startMillis);
        values.put(CalendarContract.Events.DTEND, endMillis);
        values.put(CalendarContract.Events.TITLE, event.name);
        values.put(CalendarContract.Events.DESCRIPTION, event.description.toString());
        values.put(CalendarContract.Events.CALENDAR_ID, defaultCalendarID);
        values.put(CalendarContract.Events.EVENT_TIMEZONE, "America/Los_Angeles");
        Uri uri = cr.insert(CalendarContract.Events.CONTENT_URI, values);

        // get the event ID that is the last element in the Uri
        long eventID = Long.parseLong(uri.getLastPathSegment());

        // Store the Event ID along with the event name in the shared preferences
        // This is used to ensure duplicate calendar events are not created
        prefs.edit().putLong(event.name, eventID).apply();

    }

    private void updateEvent(mmaEvent event, long eventID){

        //Update the calendar event with the new description (i.e. if new fights are added to a card)

        ContentResolver cr = getContentResolver();
        ContentValues values = new ContentValues();
        Uri updateUri = null;
        values.put(CalendarContract.Events.DESCRIPTION, event.description.toString());
        updateUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventID);
        int rows = cr.update(updateUri, values, null, null);


    }

    public void checkDefaultCalendar(){

        //Query the user's calendars to get their default calendar ID.
        //This calendar ID is necessary to automatically send intents to create/update events in the user's calendar.

        String[] projection =
                {
                        CalendarContract.Calendars.VISIBLE,
                        CalendarContract.Calendars.IS_PRIMARY,
                        CalendarContract.Calendars._ID
                };


        //Create a cursor to query user's calendars

        Cursor calCursor = getContentResolver().query(CalendarContract.Calendars.CONTENT_URI, projection, CalendarContract.Calendars.VISIBLE + " = 1 AND "  + CalendarContract.Calendars.IS_PRIMARY + "=1", null, CalendarContract.Calendars._ID + " ASC");
        if(calCursor.getCount() <= 0){
            calCursor = getContentResolver().query(CalendarContract.Calendars.CONTENT_URI, projection, CalendarContract.Calendars.VISIBLE + " = 1", null, CalendarContract.Calendars._ID + " ASC");
        }


        //Get the calendar ID from the first calendar on the returned cursor and set it as the global default calendar
        if(calCursor != null){
            calCursor.moveToNext();
            defaultCalendarID = (long) calCursor.getInt(calCursor.getColumnIndex(CalendarContract.Calendars._ID));

        }

    }

    class mmaEvent {

        //Subclass for MMA Events
        //Stores data associated with a particular event:
        //Name: Event Name
        //Date: Event Date
        //Description: List of matches between fighters

        public String name = "";
        public Date date;
        public StringBuilder description;

        public mmaEvent(String name) {
            this.name = name;
            description = new StringBuilder();
        }

        public void addFight(String fight) {
            this.description.append(fight + "\n");
        }

        public void addDate(String sDate) {

            //Convert input string date into actual date for the event

            DateFormat format = new SimpleDateFormat("MMMM d, yyyy", Locale.ENGLISH);
            try {
                this.date = format.parse(sDate);
            } catch (ParseException e) {
                Log.d("DateException", e.getMessage());
            }
        }

        @Override
        public String toString() {
            return "" + this.name + (this.date.getYear()+ 1900) + this.date.getMonth() + this.date.getDate() + this.description.toString();
        }
    }
}
