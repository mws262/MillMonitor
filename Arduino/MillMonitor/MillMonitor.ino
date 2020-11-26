#define PINS_PER_SET 8

// Optocoupler set 1, outputs 1-8 wired to these ATMEGA pins.
const uint8_t pinset1[] = {39, 41, 43, 45, 47, 49, 51, 53};

// Optocoupler set 2, outputs 1-8 wired to these ATMEGA pins.
const uint8_t pinset2[] = {38, 40, 42, 44, 46, 48, 50, 52};

const long SEND_SPACING_MS = 5000;

String inputString = "";         // a String to hold incoming data

void setup() {
  for (int i = 0; i < PINS_PER_SET; i++) {
    pinMode(pinset1[i], INPUT);
    pinMode(pinset2[i], INPUT);
  }
  inputString.reserve(200);

  pinMode(13, OUTPUT);
  Serial.begin(9600);
}

void loop() {
  for (int i = 0; i < PINS_PER_SET; i++) {
      Serial.write(digitalRead(pinset1[i]));
  }
  for (int i = 0; i < PINS_PER_SET; i++) {
      Serial.write(digitalRead(pinset2[i]));
  }

  Serial.write('\n');

  delay(SEND_SPACING_MS);
}

/*
  SerialEvent occurs whenever a new data comes in the hardware serial RX. This
  routine is run between each time loop() runs, so using delay inside loop can
  delay response. Multiple bytes of data may be available.
*/
void serialEvent() {
  while (Serial.available()) {
    // get the new byte:
    char inChar = (char)Serial.read();
    // add it to the inputString:
    inputString += inChar;
    // if the incoming character is a newline, set a flag so the main loop can
    // do something about it:
    if (inChar == '\n') {
        if (inputString.length() > 0) {
          Serial.println("HIGHFIVE");
          Serial.flush();
          inputString = "";
          digitalWrite(13, HIGH);
          delay(500);
        }
        digitalWrite(13, LOW);
    }
  }
}
