# CountSensorAndroidService

## TODO:
0. prepare a simulator that can stream values (to emaulate the sensor). mostley value=0. sometimes value = 1500 
1. Change network url at `AsyncCommunicator`
2. Prepare POST logic (once every X minutes, if there are values to be sent)
3  check data cache is ok before POST to cloud and that it persists for next attemtps if POST fails for any reason
4. add TO UI:  the state of `processSignal()`: iterator of current buffer. and number of buffers analyzed
5. auto CONNECT to sensor, on startup of the app
6. Test application behavior after reboot (make sure it starts with phone restart, and make sure it CONNECTS to sensor)
