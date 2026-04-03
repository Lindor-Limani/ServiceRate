# ServiceRate
# ServiceRateBackend

## 📌 Overview

ServiceRateBackend is a Spring Boot-based REST API developed in Java.  
It serves as the backend for managing and processing application data, including business logic, persistence, and API endpoints.

The project follows a layered architecture and uses modern Spring technologies.

---

## ⚙️ Tech Stack

- Java 21
- Spring Boot
- Spring Web (REST API)
- Spring Data JPA (Database access)
- Spring Security (Authentication & Authorization)
- H2 Database (Development)
- Gradle (Build tool)

---

## 🚀 Getting Started

### 1. Requirements

- Java 21 installed
- IntelliJ IDEA (recommended)
- Gradle (wrapper included)

---

### 2. Run the Application

Using IntelliJ:
- Run `ServiceRateBackendApplication.java`

Or via terminal:

```
bash
./gradlew bootRun
```

### Access the Application
http://localhost:8080

### Default Login
Using generated security password: xxxxxxxx
Visible in Console after starting the Backend

### Project Structure
```
src/main/java/at/hcw/serviceratebackend
 ├── controller      # REST endpoints
 ├── service         # Business logic
 ├── repository      # Database access
 ├── model           # Entities / domain objects
 ├── dto             # Data Transfer Objects
 ├── config          # Configuration (Security, etc.)
 └── ServiceRateBackendApplication.java
```


 ### Architecture
 Controller → Service → Repository → Database

# External APIs
Rules: No Keys to be pushed onto the Repeository besides those which are free to use.

## (Free) WeatherAPI:
### Link
```
https://api.openweathermap.org/data/2.5/weather?lat=44.34&lon=10.99&appid=914e9cf22f9c2eb059f9ea4adc9b3cd8
```
#### APIKEY
```
914e9cf22f9c2eb059f9ea4adc9b3cd8
```
#### Json-Response:
```
{
    "coord": {
        "lon": 10.99,
        "lat": 44.34
    },
    "weather": [
        {
            "id": 800,
            "main": "Clear",
            "description": "clear sky",
            "icon": "01d"
        }
    ],
    "base": "stations",
    "main": {
        "temp": 289.71,
        "feels_like": 288.39,
        "temp_min": 288.82,
        "temp_max": 289.86,
        "pressure": 1012,
        "humidity": 37,
        "sea_level": 1012,
        "grnd_level": 946
    },
    "visibility": 10000,
    "wind": {
        "speed": 2.33,
        "deg": 356,
        "gust": 3.3
    },
    "clouds": {
        "all": 3
    },
    "dt": 1775226003,
    "sys": {
        "type": 2,
        "id": 2004688,
        "country": "IT",
        "sunrise": 1775192030,
        "sunset": 1775238274
    },
    "timezone": 7200,
    "id": 3163858,
    "name": "Zocca",
    "cod": 200
}
```
## Google Maps
### Link
Example for Directions: 
```
https://maps.googleapis.com/maps/api/directions/json?destination=Toronto&origin=Ali's GemüseKebap, Währinger Str. 9, 1090 Wien&key={API KEY}
```
### API-KEY
not shared
### Response: 
```
{
    "geocoded_waypoints": [
        {
            "geocoder_status": "OK",
            "place_id": "ChIJPYBulbQHbUcRxs1Lw8Kz6Ck",
            "types": [
                "establishment",
                "food",
                "point_of_interest",
                "restaurant"
            ]
        },
        {
            "geocoder_status": "OK",
            "place_id": "EixLYXJsLUJlZG5hcmlrLUdhc3NlIDE0LzEsIDEyMjAgV2llbiwgQXVzdHJpYSIdGhsKFgoUChIJp3AWt0IBbUcR70MZHiHn5EgSATE",
            "types": [
                "street_address",
                "subpremise"
            ]
        }
    ],
    "routes": [
        {
            "bounds": {
                "northeast": {
                    "lat": 48.2517395,
                    "lng": 16.4850649
                },
                "southwest": {
                    "lat": 48.1913539,
                    "lng": 16.3593015
                }
            },
            "copyrights": "Powered by Google, ©2026 Google",
            "legs": [
                {
                    "distance": {
                        "text": "16.5 km",
                        "value": 16510
                    },
                    "duration": {
                        "text": "25 mins",
                        "value": 1489
                    },
                    "end_address": "Karl-Bednarik-Gasse 14/1, 1220 Wien, Austria",
                    "end_location": {
                        "lat": 48.2443634,
                        "lng": 16.4850649
                    },
                    "start_address": "Währinger Str. 9, 1090 Wien, Austria",
                    "start_location": {
                        "lat": 48.217096,
                        "lng": 16.3593015
                    },
                    "steps": [
                        {
                            "distance": {
                                "text": "0.3 km",
                                "value": 341
                            },
                            "duration": {
                                "text": "1 min",
                                "value": 86
                            },
                            "end_location": {
                                "lat": 48.21452130000001,
                                "lng": 16.3617593
                            },
                            "html_instructions": "Head <b>southeast</b> on <b>Währinger Str.</b> toward <b>Rooseveltplatz</b>",
                            "polyline": {
                                "points": "{kheHsdzbBXY`@a@RU`@_@@C@ADEXYHE@APQRQBCFGFEFCHGLGRKRMNIJI@CVUVULMPQLQFILMf@i@TUFMFK"
                            },
                            "start_location": {
                                "lat": 48.217096,
                                "lng": 16.3593015
                            },
                            "travel_mode": "DRIVING"
                        },
                        {
                            "distance": {
                                "text": "84 m",
                                "value": 84
                            },
                            "duration": {
                                "text": "1 min",
                                "value": 35
                            },
                            "end_location": {
                                "lat": 48.2139737,
                                "lng": 16.3625145
                            },
                            "html_instructions": "Continue onto <b>Schottengasse</b>",
                            "polyline": {
                                "points": "w{geH_tzbBHUBG@E@C@C@ABIFOHQ^]JITS"
                            },
                            "start_location": {
                                "lat": 48.21452130000001,
                                "lng": 16.3617593
                            },
                            "travel_mode": "DRIVING"
                        },
                        {
                            "distance": {
                                "text": "0.7 km",
                                "value": 685
                            },
                            "duration": {
                                "text": "2 mins",
                                "value": 117
                            },
                            "end_location": {
                                "lat": 48.2174511,
                                "lng": 16.3701467
                            },
                            "html_instructions": "Turn <b>left</b> onto <b>Schottenring</b>",
                            "maneuver": "turn-left",
                            "polyline": {
                                "points": "ixgeHuxzbBKYEKWs@_@iAc@qA]aAaAmCi@{AQg@Ws@w@yB_@aAQg@IUs@oB{@cCM[o@kBa@gAACGQWs@Qi@M[CGAGCICIAG"
                            },
                            "start_location": {
                                "lat": 48.2139737,
                                "lng": 16.3625145
                            },
                            "travel_mode": "DRIVING"
                        },
                        {
                            "distance": {
                                "text": "1.3 km",
                                "value": 1278
                            },
                            "duration": {
                                "text": "3 mins",
                                "value": 184
                            },
                            "end_location": {
                                "lat": 48.2113656,
                                "lng": 16.3828802
                            },
                            "html_instructions": "Continue onto <b>Franz-Josefs-Kai</b>",
                            "polyline": {
                                "points": "anheHmh|bBCKCIAKCMAM?IAG?I?M?A?M?I?I@G@QBe@NY\\m@LQHMJQRUX]LOXYTWVUTU\\[TQXWROBALKNKZSh@]b@UPKf@UPKNGf@Wt@c@FGFCBCDGBCHIHGHGPODCHITUDEHGHIHMZ]HIV[PYDELSPWHMLWTc@JWDKBCHQFSFOFOL]HYJ]BMBGF]Lm@Ns@Ls@Hk@@IPcA@GD]DUFm@Hw@B]@U@O?G@U@U?_@@_A@o@?U@k@Bc@@u@?_@DoA?o@@u@BqA"
                            },
                            "start_location": {
                                "lat": 48.2174511,
                                "lng": 16.3701467
                            },
                            "travel_mode": "DRIVING"
                        },
                        {
                            "distance": {
                                "text": "0.4 km",
                                "value": 361
                            },
                            "duration": {
                                "text": "1 min",
                                "value": 44
                            },
                            "end_location": {
                                "lat": 48.2124668,
                                "lng": 16.3868702
                            },
                            "html_instructions": "Continue onto <b>Uraniastraße</b>/<wbr/><b>B227</b><div style=\"font-size:0.9em\">Continue to follow B227</div>",
                            "polyline": {
                                "points": "ahgeH_x~bBBk@DmA@QJ{CFgA?SAK?KCKCKI[EMKUGSGQAIESKc@CMEOGOCGCICCCGACIICEGGECGEEEGCUKOIMIKIKIGIGG"
                            },
                            "start_location": {
                                "lat": 48.2113656,
                                "lng": 16.3828802
                            },
                            "travel_mode": "DRIVING"
                        },
                        {
                            "distance": {
                                "text": "0.2 km",
                                "value": 168
                            },
                            "duration": {
                                "text": "1 min",
                                "value": 14
                            },
                            "end_location": {
                                "lat": 48.2129711,
                                "lng": 16.3889657
                            },
                            "html_instructions": "Continue onto <b>Dampfschiffstraße</b>",
                            "polyline": {
                                "points": "}ngeH}p_cBIKCIEKEMEOKm@SmAESEYAOEYC[Ec@Am@Ca@"
                            },
                            "start_location": {
                                "lat": 48.2124668,
                                "lng": 16.3868702
                            },
                            "travel_mode": "DRIVING"
                        },
                        {
                            "distance": {
                                "text": "3.0 km",
                                "value": 2977
                            },
                            "duration": {
                                "text": "4 mins",
                                "value": 257
                            },
                            "end_location": {
                                "lat": 48.1943553,
                                "lng": 16.4130968
                            },
                            "html_instructions": "Keep <b>left</b> to continue on <b>Dampfschiffstraße</b>/<wbr/><b>B227</b><div style=\"font-size:0.9em\">Continue to follow B227</div>",
                            "maneuver": "keep-left",
                            "polyline": {
                                "points": "argeHa~_cBC_@IaB?Q?O@WBk@?ABi@@]BU?K@MDc@RcBDg@Hw@F_@BWBQ@KBODM@IBG@GBIBGBGDKDKDKFKDIDGFKHKFINQNQ@ALKPOh@e@RQHINKTQh@c@HEROLIl@_@XORMBAf@[RKHEDCLI|@c@f@U`@Q^Q\\OPGPIv@W^MVIRGREVEJAF?JA`@Ch@ITAVEp@IXExASbAONAl@G|@IZGl@KZEd@ONGl@]LGd@[LKDCJKZ]^_@VYRWDCFIN[HMNWf@{@`@_ABG\\_ALg@Ja@DQDWJo@BMFg@ZyC^qDBKXaCXkCBMBUh@aEVaBTiAHa@HWL_@HUPe@DIL]R_@PYLSPWRUFIHINMLKBEBA@ANMTOv@g@|@s@ZU`As@?A`@YLK^YTS`Au@ZYJIb@c@n@k@n@k@@C`@]XY^_@PO^e@HGd@k@d@o@DG\\i@NS?AHKPWZ_@NSZi@^e@z@yA^m@f@eA"
                            },
                            "start_location": {
                                "lat": 48.2129711,
                                "lng": 16.3889657
                            },
                            "travel_mode": "DRIVING"
                        },
                        {
                            "distance": {
                                "text": "43 m",
                                "value": 43
                            },
                            "duration": {
                                "text": "1 min",
                                "value": 3
                            },
                            "end_location": {
                                "lat": 48.1940542,
                                "lng": 16.41343
                            },
                            "html_instructions": "Slight <b>right</b> onto the ramp to <b>Tschechien</b>/<wbr/><b>Praha</b>/<wbr/><b>Brno</b>/<wbr/><b>Italien</b>/<wbr/><b>Slowenien</b>/<wbr/><b>Deutschland</b>/<wbr/><b>Graz</b>/<wbr/><b>Linz</b>",
                            "maneuver": "ramp-right",
                            "polyline": {
                                "points": "w}ceH{tdcBPE@?DIDGBEFKLUBA"
                            },
                            "start_location": {
                                "lat": 48.1943553,
                                "lng": 16.4130968
                            },
                            "travel_mode": "DRIVING"
                        },
                        {
                            "distance": {
                                "text": "66 m",
                                "value": 66
                            },
                            "duration": {
                                "text": "1 min",
                                "value": 4
                            },
                            "end_location": {
                                "lat": 48.1935325,
                                "lng": 16.4138256
                            },
                            "html_instructions": "Take the ramp to <b>A23</b>/<wbr/><b>E59</b>",
                            "polyline": {
                                "points": "y{ceH}vdcBHMDCFGLKHGJILGFEHEDADA"
                            },
                            "start_location": {
                                "lat": 48.1940542,
                                "lng": 16.41343
                            },
                            "travel_mode": "DRIVING"
                        },
                        {
                            "distance": {
                                "text": "7.0 km",
                                "value": 7012
                            },
                            "duration": {
                                "text": "6 mins",
                                "value": 361
                            },
                            "end_location": {
                                "lat": 48.2403582,
                                "lng": 16.4601194
                            },
                            "html_instructions": "Slight <b>left</b> to merge onto <b>A23</b>/<wbr/><b>E59</b> toward <b>E49</b>/<wbr/><b>Tschechien</b>/<wbr/><b>Praha</b>/<wbr/><b>A22</b>/<wbr/><b>Brno</b>/<wbr/><b>A5</b>/<wbr/><b>E461</b>/<wbr/><b>Handelskai</b>/<wbr/><b>B14</b><div style=\"font-size:0.9em\">Continue to follow A23</div><div style=\"font-size:0.9em\">Toll road</div>",
                            "maneuver": "ramp-left",
                            "polyline": {
                                "points": "qxceHmydcBDI?ANMDE@CDGFKBG@GDMJ[FWFWJi@BIHYDMLc@Ne@?A\\y@Vg@LUZk@Xa@?ABCLORQLGRC@?DAF?LBB@HBHHDDRZH^DZAb@C\\AD?DCF?BCFADCFCBCDABCDA@A@CBCBCBEBC@C@C@C?E@E?C?C?C?EAGAGCECEAGEMGECECEECEECCEEEEGCEEKMOGKACIOKOIMEIEGW_@u@oAU]_@i@GM_A_BWa@o@aAA?e@]e@w@QYSY[k@]g@U_@KOMUg@w@CEgBqCk@iAGMGKACCCACAAACCCMSMUwA}BuA_CS[IMEIU]KQOWq@gAMUMSyAcCmB}CQ[KQKQOWGIwAaCyAaCCEKQIMCECECEKSMSKQ]k@cFsIKQgDwFIOsAwBsAyBw@qASYq@kAOUWa@AC]i@MSMSMSS_@S_@KOKQOWMQKQa@i@uIoNo@gAo@eAMUMU_A}As@eAe@q@eAiBgAeB[i@]i@Ua@Uc@m@gAMUSYU_@g@w@S[w@uAuA}BoAsBe@u@Q[U_@Yi@_@q@qAgBU[UYAASUSSIIMKeAw@UOWMYMOIQIUISGm@Mw@CiAES@C@Q@c@@u@?iABc@?o@?]?A?{@CM?Y?o@?q@?E?eBA}B?_A?c@Ac@A_@AWCA?YCC?c@IICWG_@KCAq@Wa@SA?SKMEYMi@SqAg@]OaBk@w@WGCuAa@KESGUGECUIQGCAYOSK_@SMIUOIGKIUQKKCAIIKKQQOOKMKMUWMQKM}@gAOQGIEEEEAAMMMOMMMKGEMKWQQMQKWOQKCASIUM{@]WI]OoAc@iC}@CAYK_@Kc@OIEWKi@Qi@O]KMEQG_AYs@U{Ac@SGC?e@OQG]My@[g@USI]Q[Qa@WQKOIGGc@[]WYU_@[[Yw@y@k@o@_@c@g@q@a@i@U[k@y@e@k@CG]_@UW[YWSIIIEIIYSkFsCGEe@U_Ag@[OSKEE_Ae@k@[a@Sg@W[Qa@Ua@SUMg@We@WCA"
                            },
                            "start_location": {
                                "lat": 48.1935325,
                                "lng": 16.4138256
                            },
                            "travel_mode": "DRIVING"
                        },
                        {
                            "distance": {
                                "text": "1.3 km",
                                "value": 1251
                            },
                            "duration": {
                                "text": "1 min",
                                "value": 66
                            },
                            "end_location": {
                                "lat": 48.249548,
                                "lng": 16.4697566
                            },
                            "html_instructions": "Continue onto <b>S2</b><div style=\"font-size:0.9em\">Toll road</div>",
                            "polyline": {
                                "points": "g}leHwzmcBm@[]SQKQKSMUMUOWOGEGEIGo@e@AAc@]IG[WOMIG[W[W[WMM{@y@OOa@_@UW]]MOQQ]_@[_@QU_@c@OQGIY_@_@e@OUSU}B{CQWqBkCi@s@s@}@[c@SWIMOUIICEEGCCEGs@_Ag@o@g@q@k@o@IKEGGIIKIKk@o@SSY[{@_Ak@k@s@u@US"
                            },
                            "start_location": {
                                "lat": 48.2403582,
                                "lng": 16.4601194
                            },
                            "travel_mode": "DRIVING"
                        },
                        {
                            "distance": {
                                "text": "0.3 km",
                                "value": 284
                            },
                            "duration": {
                                "text": "1 min",
                                "value": 42
                            },
                            "end_location": {
                                "lat": 48.2516919,
                                "lng": 16.471799
                            },
                            "html_instructions": "Take exit <b>Breitenlee</b> toward <b>Gewerbepark Kagran</b>",
                            "maneuver": "ramp-right",
                            "polyline": {
                                "points": "uvneH_wocBGS?AKMIGOOGGOQ[]IKOMQMOMUQKKo@i@MKu@q@q@k@[WMK[W"
                            },
                            "start_location": {
                                "lat": 48.249548,
                                "lng": 16.4697566
                            },
                            "travel_mode": "DRIVING"
                        },
                        {
                            "distance": {
                                "text": "0.8 km",
                                "value": 835
                            },
                            "duration": {
                                "text": "2 mins",
                                "value": 114
                            },
                            "end_location": {
                                "lat": 48.2514608,
                                "lng": 16.4830348
                            },
                            "html_instructions": "Turn <b>right</b> onto <b>Breitenleer Str.</b>",
                            "maneuver": "turn-right",
                            "polyline": {
                                "points": "adoeHwcpcB?Q?SA]Co@?oAAs@@WAiA?M?]?U@Y?G?CAE?EACAE@U@YB{@@[@a@Bi@Bq@Bk@Be@?[Do@PkDBm@B[?O@Q@}BA{CAcB?{@?e@?UAgA?}@?wG?mA@i@Ae@"
                            },
                            "start_location": {
                                "lat": 48.2516919,
                                "lng": 16.471799
                            },
                            "travel_mode": "DRIVING"
                        },
                        {
                            "distance": {
                                "text": "0.6 km",
                                "value": 553
                            },
                            "duration": {
                                "text": "1 min",
                                "value": 70
                            },
                            "end_location": {
                                "lat": 48.2472344,
                                "lng": 16.4791083
                            },
                            "html_instructions": "Turn <b>right</b> onto <b>Spargelfeldstraße</b>",
                            "maneuver": "turn-right",
                            "polyline": {
                                "points": "sboeH}ircBJJB@FDNJ^Zb@b@n@j@JLJHTTlCbCdA~@l@h@BBz@x@n@l@NL`@\\nAhAJLhAbAx@r@"
                            },
                            "start_location": {
                                "lat": 48.2514608,
                                "lng": 16.4830348
                            },
                            "travel_mode": "DRIVING"
                        },
                        {
                            "distance": {
                                "text": "0.4 km",
                                "value": 361
                            },
                            "duration": {
                                "text": "1 min",
                                "value": 47
                            },
                            "end_location": {
                                "lat": 48.2454974,
                                "lng": 16.483225
                            },
                            "html_instructions": "Turn <b>left</b> onto <b>Bibernellweg</b>",
                            "maneuver": "turn-left",
                            "polyline": {
                                "points": "ehneHmqqcBRq@J_@b@oAl@oBdBuFZ{@v@eCd@uAFU"
                            },
                            "start_location": {
                                "lat": 48.2472344,
                                "lng": 16.4791083
                            },
                            "travel_mode": "DRIVING"
                        },
                        {
                            "distance": {
                                "text": "0.1 km",
                                "value": 124
                            },
                            "duration": {
                                "text": "1 min",
                                "value": 24
                            },
                            "end_location": {
                                "lat": 48.2448135,
                                "lng": 16.4844814
                            },
                            "html_instructions": "Continue onto <b>Karl-Bednarik-Gasse</b>",
                            "polyline": {
                                "points": "k}meHckrcBb@oADGLIJIHEFEBEBEBGDMH[Lk@HY"
                            },
                            "start_location": {
                                "lat": 48.2454974,
                                "lng": 16.483225
                            },
                            "travel_mode": "DRIVING"
                        },
                        {
                            "distance": {
                                "text": "26 m",
                                "value": 26
                            },
                            "duration": {
                                "text": "1 min",
                                "value": 5
                            },
                            "end_location": {
                                "lat": 48.2446043,
                                "lng": 16.4843228
                            },
                            "html_instructions": "Turn <b>right</b>",
                            "maneuver": "turn-right",
                            "polyline": {
                                "points": "aymeH_srcBh@^"
                            },
                            "start_location": {
                                "lat": 48.2448135,
                                "lng": 16.4844814
                            },
                            "travel_mode": "DRIVING"
                        },
                        {
                            "distance": {
                                "text": "61 m",
                                "value": 61
                            },
                            "duration": {
                                "text": "1 min",
                                "value": 16
                            },
                            "end_location": {
                                "lat": 48.2443634,
                                "lng": 16.4850649
                            },
                            "html_instructions": "Turn <b>left</b><div style=\"font-size:0.9em\">Destination will be on the right</div>",
                            "maneuver": "turn-left",
                            "polyline": {
                                "points": "wwmeH_rrcBn@sC"
                            },
                            "start_location": {
                                "lat": 48.2446043,
                                "lng": 16.4843228
                            },
                            "travel_mode": "DRIVING"
                        }
                    ],
                    "traffic_speed_entry": [],
                    "via_waypoint": []
                }
            ],
            "overview_polyline": {
                "points": "{kheHsdzbBtCwCp@k@ZU~@i@t@m@dAgArAwANYL]FOJYHQ^]`@]Qe@yBqGuCeIaHuRiBgFMg@Kq@AmABYBe@NYj@_AT_@tA}A`B_BfA{@bBiAnBcAv@_@|@k@TSn@i@j@i@x@}@x@eAv@qAlAqCf@cBh@gCj@mDTiBP{BBsADqDJiE@eBF}BZcIEw@_@kAu@uCO]W[[S_Ai@e@g@Us@k@iDQiBSqEDeBP}Cx@iH^mAXm@^i@n@q@xAqAxAgAhBiAnDoBfCiAzBw@j@Qj@K`AG`IeA|@I|@IZGhAQt@Wz@e@r@g@lAmAx@_ApA}Bd@gAj@gBb@{BbCiTl@wEl@kDj@oBVo@`@}@^m@d@m@n@m@XWlAw@xAiApB{ArC}BnCeC|A{A`BiBxAuBv@eAj@}@^e@zAgCf@eAPEFIHMb@q@d@_@j@]JCDKVWLSDO`@yA^{A\\iA\\{@`AiBj@w@`@YTCLAPDRLX`@Nz@E`AEVMXKPMLQHO@QA[Ka@USUw@oAeDgFoCqEg@]w@qAcBmCeAcBgBqCk@iAOYIMmFyIwBoDkNmU_NiUwIqN}ByDu@mAa@i@uIoN_BmC[k@sBcDoEkHwBwD_BgCqFcJgB{CqByCk@u@s@u@sAcAm@]qAk@aAUaCImAF_CBsA?iBCeLA_CIcAMeAYiBw@cDoA_EwA}DoA{Ay@aAs@e@c@y@}@qCgDk@m@{@o@mAu@iBw@qJeDuBs@}Bs@gDaAoCaAuBcAkAu@{BeBsAsAkAsAkCqDi@s@s@w@gA}@c@]sFyCuCyAsC{AoFsCgEcCyBaBwCcCcCaC{A_BoDqEcLcOkBgCgDgEuC_DuBuBGUUUcAgA{CiCqCaC[W?QAq@CkEAkC?k@AI?[HsCNmDDkATyEF{DEuW@wBAe@JJJFn@f@jBfBvGbGfIrHx@r@Rq@n@oBfFgPl@kBb@oADGXSPKPa@VgAHYh@^n@sC"
            },
            "summary": "A23",
            "warnings": [],
            "waypoint_order": []
        }
    ],
    "status": "OK"
}
```
