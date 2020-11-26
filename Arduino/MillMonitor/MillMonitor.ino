#define PINS_PER_SET 8

// Optocoupler set 1, outputs 1-8 wired to these ATMEGA pins.
const uint8_t pinset1[] = {39, 41, 43, 45, 47, 49, 51, 53};

// Optocoupler set 2, outputs 1-8 wired to these ATMEGA pins.
const uint8_t pinset2[] = {38, 40, 42, 44, 46, 48, 50, 52};

const long SEND_SPACING_MS = 5000;
void setup() {
  for (int i = 0; i < PINS_PER_SET; i++) {
    pinMode(pinset1[i], INPUT);
    pinMode(pinset2[i], INPUT);
  }

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
