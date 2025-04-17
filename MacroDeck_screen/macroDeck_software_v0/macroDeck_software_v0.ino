// --- CÓDIGO PARA RASPBERRY PI PICO ---
#include <TFT_eSPI.h>
#include <SPI.h>
#include <string.h>
#include "Adafruit_TinyUSB.h"

TFT_eSPI tft = TFT_eSPI();
TFT_eSPI_Button btn1, btn2;
Adafruit_USBD_HID usb_hid;

const int btnWidth = 180;
const int btnHeight = 80;
const int btnY = 160;
const int btn1X = 120;
const int btn2X = 360;

String title1 = "";
String title2 = "";
bool wasTouched = false;
bool alreadyFired = false;
bool receivingTitle = false;
int currentTarget = 0;
String tempBuffer;

uint8_t const desc_hid_report[] = {
  TUD_HID_REPORT_DESC_KEYBOARD()
};

void drawButtonWithText(TFT_eSPI_Button& btn, int x, int y, uint16_t color, const String& text) {
  btn.initButton(&tft, x, y, btnWidth, btnHeight, TFT_WHITE, color, TFT_WHITE, (char*)"", 2);
  btn.drawButton();
  if (text.length() > 0) {
    tft.setTextDatum(MC_DATUM);
    tft.setTextColor(TFT_WHITE, color);
    tft.drawString(text, x, y);
  }
}

void sendKeyCombo(uint8_t keycode) {
  if (!usb_hid.ready()) return;
  uint8_t report[8] = { 0 };
  report[2] = keycode;
  usb_hid.sendReport(0, report, sizeof(report));
  delay(10);
  memset(report, 0, sizeof(report));
  usb_hid.sendReport(0, report, sizeof(report));
}

void setup() {
  Serial.begin(115200);
  usb_hid.setReportDescriptor(desc_hid_report, sizeof(desc_hid_report));
  usb_hid.setPollInterval(2);
  usb_hid.begin();

  tft.init();
  tft.setRotation(1);
  tft.fillScreen(TFT_BLACK);

  drawButtonWithText(btn1, btn1X, btnY, TFT_RED, title1);
  drawButtonWithText(btn2, btn2X, btnY, TFT_BLUE, title2);
  Serial.println("READY");
}

void loop() {
  if (Serial.available()) {
    String line = Serial.readStringUntil('\n');
    line.trim();

    if (line == "TITLE1") {
      receivingTitle = true;
      currentTarget = 1;
      tempBuffer = "";
      return;
    } else if (line == "TITLE2") {
      receivingTitle = true;
      currentTarget = 2;
      tempBuffer = "";
      return;
    } else if (line == "END") {
      receivingTitle = false;
      if (currentTarget == 1) title1 = tempBuffer;
      else if (currentTarget == 2) title2 = tempBuffer;

      drawButtonWithText(btn1, btn1X, btnY, TFT_RED, title1);
      drawButtonWithText(btn2, btn2X, btnY, TFT_BLUE, title2);
      Serial.println(currentTarget == 1 ? "OK_TITLE1" : "OK_TITLE2");
      return;
    }

    if (receivingTitle) {
      tempBuffer = line;
    }
  }

  uint16_t x, y;
  bool touched = tft.getTouch(&x, &y);
  if (touched && !wasTouched) {
    wasTouched = true;
    if (btn1.contains(x, y)) {
      sendKeyCombo(HID_KEY_F13);
      drawButtonWithText(btn1, btn1X, btnY, TFT_RED, title1);
      alreadyFired = true;
    }
    if (btn2.contains(x, y)) {
      sendKeyCombo(HID_KEY_F14);
      drawButtonWithText(btn2, btn2X, btnY, TFT_BLUE, title2);
      alreadyFired = true;
    }
  } else if (!touched) {
    wasTouched = false;
    alreadyFired = false;
  }
}
