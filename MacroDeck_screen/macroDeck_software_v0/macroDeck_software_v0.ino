#include <TFT_eSPI.h>
#include <SPI.h>
#include <string.h>
#include "Adafruit_TinyUSB.h"

TFT_eSPI tft = TFT_eSPI();
TFT_eSPI_Button buttons[12];
Adafruit_USBD_HID usb_hid;

#define TOUCH_MIN_X 200
#define TOUCH_MAX_X 3912
#define TOUCH_MIN_Y 186
#define TOUCH_MAX_Y 3918
#define TFT_MIN_X 0
#define TFT_MAX_X 479
#define TFT_MIN_Y 0
#define TFT_MAX_Y 319

const int btnWidth = 90;
const int btnHeight = 90;
const int margin = 15;
const int spacing = 10;
const int cols = 4;
const int rows = 3;

String titles[12];
bool wasTouched = false;
bool alreadyFired = false;
bool receivingTitle = false;
int currentTarget = 0;
String tempBuffer;

bool receivingImage = false;
int imageTarget = 0;
uint8_t imageBuffer[90 * 90 * 2];
int imageIndex = 0;
bool iconDrawn[12] = {false};

uint8_t const desc_hid_report[] = {
  TUD_HID_REPORT_DESC_KEYBOARD()
};


void drawButtonWithText(int i, int x, int y, uint16_t color, const String& text) {
  buttons[i].initButton(&tft, x, y, btnWidth, btnHeight, TFT_WHITE, color, TFT_WHITE, (char*)"", 2);
  buttons[i].drawButton();
  if (text.length() > 0) {
    tft.setTextDatum(MC_DATUM);
    tft.setTextColor(TFT_WHITE, color);
    tft.drawString(text, x, y);
  }
}

void drawIcon(int index) {
  int row = index / cols;
  int col = index % cols;
  int totalWidth = cols * btnWidth + (cols - 1) * spacing;
  int offsetX = (TFT_MAX_X + 1 - totalWidth) / 2;
  int x = offsetX + col * (btnWidth + spacing) + btnWidth / 2;
  int y = margin + row * (btnHeight + spacing) + btnHeight / 2;
  int startX = x - 45;
  int startY = y - 45;
  int radius = 12;

  tft.startWrite();
  for (int py = 0; py < 90; ++py) {
    for (int px = 0; px < 90; ++px) {
      int idx = (py * 90 + px) * 2;
      uint16_t color = imageBuffer[idx] | (imageBuffer[idx + 1] << 8);

      bool skip =
        (px < radius && py < radius && (px - radius) * (px - radius) + (py - radius) * (py - radius) > radius * radius) ||
        (px >= 90 - radius && py < radius && (px - (90 - radius - 1)) * (px - (90 - radius - 1)) + (py - radius) * (py - radius) > radius * radius) ||
        (px < radius && py >= 90 - radius && (px - radius) * (px - radius) + (py - (90 - radius - 1)) * (py - (90 - radius - 1)) > radius * radius) ||
        (px >= 90 - radius && py >= 90 - radius && (px - (90 - radius - 1)) * (px - (90 - radius - 1)) + (py - (90 - radius - 1)) * (py - (90 - radius - 1)) > radius * radius);

      if (!skip) {
        tft.drawPixel(startX + px, startY + py, color);
      }
    }
  }
  tft.endWrite();
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
  uint16_t calData[5] = { TOUCH_MIN_X, TOUCH_MAX_X, TOUCH_MIN_Y, TOUCH_MAX_Y, 7 };
  tft.setTouch(calData);
  tft.fillScreen(TFT_BLACK);

  int totalWidth = cols * btnWidth + (cols - 1) * spacing;
  int offsetX = (TFT_MAX_X + 1 - totalWidth) / 2;

  for (int i = 0; i < 12; i++) {
    int row = i / cols;
    int col = i % cols;
    int x = offsetX + col * (btnWidth + spacing) + btnWidth / 2;
    int y = margin + row * (btnHeight + spacing) + btnHeight / 2;
    drawButtonWithText(i, x, y, tft.color565(50 * i, 100, 255 - 20 * i), titles[i]);
  }
  Serial.println("READY");
}

void loop() {
  while (Serial.available()) {
    if (receivingImage) {
      while (Serial.available() && imageIndex < sizeof(imageBuffer)) {
        imageBuffer[imageIndex++] = Serial.read();
      }

      if (imageIndex >= sizeof(imageBuffer)) {
        receivingImage = false;
        if (imageTarget >= 1 && imageTarget <= 12) iconDrawn[imageTarget - 1] = true;
        drawIcon(imageTarget - 1);
        Serial.println("OK_ICON" + String(imageTarget));
      }
      return;
    }

    String line = Serial.readStringUntil('\n');
    line.trim();

    if (line.startsWith("TITLE")) {
      receivingTitle = true;
      currentTarget = line.substring(5).toInt();
      tempBuffer = "";
      return;
    } else if (line.startsWith("ICON")) {
      receivingImage = true;
      imageTarget = line.substring(4).toInt();
      imageIndex = 0;
      return;
    } else if (line == "END") {
      if (receivingTitle && currentTarget >= 1 && currentTarget <= 12) {
        receivingTitle = false;
        titles[currentTarget - 1] = tempBuffer;
        int row = (currentTarget - 1) / cols;
        int col = (currentTarget - 1) % cols;
        int totalWidth = cols * btnWidth + (cols - 1) * spacing;
        int offsetX = (TFT_MAX_X + 1 - totalWidth) / 2;
        int x = offsetX + col * (btnWidth + spacing) + btnWidth / 2;
        int y = margin + row * (btnHeight + spacing) + btnHeight / 2;
        drawButtonWithText(currentTarget - 1, x, y, tft.color565(50 * (currentTarget - 1), 100, 255 - 20 * (currentTarget - 1)), titles[currentTarget - 1]);
        if (iconDrawn[currentTarget - 1]) drawIcon(currentTarget - 1);
        Serial.println("OK_TITLE" + String(currentTarget));
      }
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
    int totalWidth = cols * btnWidth + (cols - 1) * spacing;
    int offsetX = (TFT_MAX_X + 1 - totalWidth) / 2;
    for (int i = 0; i < 12; i++) {
      if (buttons[i].contains(x, y)) {
        sendKeyCombo(HID_KEY_F13 + i);
        int row = i / cols;
        int col = i % cols;
        int bx = offsetX + col * (btnWidth + spacing) + btnWidth / 2;
        int by = margin + row * (btnHeight + spacing) + btnHeight / 2;
        drawButtonWithText(i, bx, by, TFT_DARKGREY, titles[i]);
        if (iconDrawn[i]) drawIcon(i);
        delay(100);
        drawButtonWithText(i, bx, by, tft.color565(50 * i, 100, 255 - 20 * i), titles[i]);
        if (iconDrawn[i]) drawIcon(i);
        break;
      }
    }
  } else if (!touched) {
    wasTouched = false;
    alreadyFired = false;
  }
}
