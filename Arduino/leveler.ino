#include <MPU9250.h>
#include <AltSoftSerial.h>

// an MPU9250 object with the MPU-9250 sensor on I2C bus 0 with address 0x68
MPU9250 IMU(Wire,0x68);
int IMUstatus;

AltSoftSerial BTserial; 
char c=' ';
boolean NL = true;
boolean isIMUActived = false;

void setup() {
  // serial to display data
  Serial.begin(115200);
  while(!Serial) {}

  Serial.print("Sketch:   ");   Serial.println(__FILE__);
  Serial.print("Uploaded: ");   Serial.println(__DATE__);

  // start communication with IMU 
  IMUstatus = IMU.begin();
  if (IMUstatus < 0) {
    Serial.println("IMU initialization unsuccessful");
    Serial.println("Check IMU wiring or try cycling power");
    Serial.print("Status: ");
    Serial.println(IMUstatus);
  }
  else {
    isIMUActived = true;
  }

    BTserial.begin(9600);  
    Serial.println("BTserial started at 9600");
}

unsigned long prevTime = 0;

void loop() {
  unsigned long t = millis();
  static unsigned long ts = 0;
  ts += t - prevTime;
  if(ts > 50) {
    ts = 0;
    String json;
    if(isIMUActived) {
      // read the sensor
      IMU.readSensor();
      // display the data
      json = String("{\"error\":{\"code\":100, \"detail\":\"OK.\"},");
      json += String("\"accel\":{\"x\":\"");
      json += String(IMU.getAccelX_mss(), 10);
      json += "\",\"y\":\"";
      json += String(IMU.getAccelY_mss(), 10);
      json += "\",\"z\":\"";
      json += String(IMU.getAccelZ_mss(), 10);
      json += "\"},\"gyro\":{\"x\":\"";
      json += String(IMU.getGyroX_rads(), 10);
      json += "\",\"y\":\"";
      json += String(IMU.getGyroY_rads(), 10);
      json += "\",\"z\":\"";
      json += String(IMU.getGyroZ_rads(), 10);
      json += "\"},\"mag\":{\"x\":\"";
      json += String(IMU.getMagX_uT(), 10);
      json += "\",\"y\":\"";
      json += String(IMU.getMagY_uT(), 10);
      json += "\",\"z\":\"";
      json += String(IMU.getMagZ_uT(), 10);
      json += "\"},\"temp\":\"";
      json += String(IMU.getTemperature_C(), 10);
      json += "\"}";
    }
    else {
      json = String("{\"error\":{\"code\":503, \"detail\":\"IMU initialization unsuccessful. Check IMU wiring or try cycling power.\", \"IMUStatus\":");
      json += String(IMUstatus);
      json += "}}";
    }
    Serial.println(json.c_str());

    String head = String("length:");
    head += String(json.length());
    for(int i=0; i < head.length(); i ++) {
      BTserial.write(head.charAt(i));
    }
    BTserial.write('\n');
    for(int i=0; i < json.length(); i ++) {
      BTserial.write(json.charAt(i));
    }
    BTserial.write('\n');
  }
  prevTime = millis();
}
