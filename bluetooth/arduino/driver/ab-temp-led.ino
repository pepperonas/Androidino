#include "DHT.h"

#define DHTPIN 9
#define DHTTYPE DHT22 // DHT11, DHT21, DHT22

DHT dht(DHTPIN, DHTTYPE);


char data = 0; // store received bluetooth data


void setup() {

    Serial.begin(9600); // set the data rate in bits per second (baud) for serial data transmission
    Serial.println("Setting up bluetooth and temperature sensor.");

    pinMode(13, OUTPUT); // set digital pin 13 as output pin

    dht.begin(); // set up the temperature sensor

}


void loop() {

    // Bluetooth connection
    if (Serial.available() > 0) {
    // send data only when you receive data
        data = Serial.read(); // read the incoming data and store it into variable data
        Serial.print(data); // print Value inside data in Serial monitor
        Serial.print("\n");
        if (data == '1') {
            digitalWrite(13, HIGH); // if 1 then turn ON LED
        }
        else if (data == '0') {
            digitalWrite(13, LOW); // if 0 then turn OFF LED
        }
    }

    // Temperature
    float h = dht.readHumidity();
    float t = dht.readTemperature();

    if (isnan(t) || isnan(h)) {
    // invalid
        Serial.println("Error while reading sensor.");
    } else {
        // send
        Serial.print(t);
        Serial.println("C");
    }

}
