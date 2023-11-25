# GraphHopper Navigation Example for Android

Use the GraphHopper Directions API with the [Maplibre Navigation SDK for Android](https://github.com/maplibre/maplibre-navigation-android).

With this SDK you can implement turn-by-turn navigation in your own Android app while hosting your Map tiles and Directions API.

<div align="center">
  <img src="https://github.com/maplibre/maplibre-navigation-android/blob/main/.github/preview.png" height="350px" alt="MapLibre Navigation Android">
</div>

## Getting Started

1. download IntelliJ with Android Support and open this repository
2. go to app/src/main/res/value/developer-config.xml and..
3. replace the GraphHopper API key to make the routing working
4. replace the map tiles API URL to make the map visible
5. enable GPS location
6. start the app and click "Navigation UI"
7. you will see a gray circle for your current location. Tap on screen to set the destination
8. Now a button "Start Route" will appear. Click it and the navigation panel will appear

## Legacy

To find the old example that uses the old GraphHopper SDK have a look [here](https://github.com/graphhopper/graphhopper-navigation-example-legacy).

## License

This code was forked from Maplibre. It stands under the [same license as the Maplibre SDK](https://github.com/maplibre/maplibre-navigation-android#license), i.e. MIT License.