package ch.heigvd.iict.dma.labo4.ble

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.content.Context
import android.util.Log
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.data.Data
import java.util.*

class DMABleManager(applicationContext: Context, private val dmaServiceListener: DMAServiceListener? = null) : BleManager(applicationContext) {

    //Services and Characteristics of the SYM Pixl
    private var timeService: BluetoothGattService? = null
    private var symService: BluetoothGattService? = null
    private var currentTimeChar: BluetoothGattCharacteristic? = null
    private var integerChar: BluetoothGattCharacteristic? = null
    private var temperatureChar: BluetoothGattCharacteristic? = null
    private var buttonClickChar: BluetoothGattCharacteristic? = null

    fun requestDisconnection() {
        this.disconnect().enqueue()
    }

    override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {

        /*
        - Nous devons vérifier ici que le périphérique auquel on vient de se connecter possède
          bien tous les services et les caractéristiques attendus, on vérifiera aussi que les
          caractéristiques présentent bien les opérations attendues
        - On en profitera aussi pour garder les références vers les différents services et
          caractéristiques (déclarés en lignes 14 à 19)
        */

        Log.d(TAG, "isRequiredServiceSupported - discovered services:")

        for (service in gatt.services) {
            Log.d("SERVICE", service.uuid.toString())
            when (service.uuid.toString()) {
                "00001805-0000-1000-8000-00805f9b34fb" -> timeService   = service
                "3c0a1000-281d-4b48-b2a7-f15579a1c38f" -> symService    = service
            }

            for (caracteristic in service.characteristics) {
                Log.d("CARACTERISTIC", caracteristic.uuid.toString())
                when (caracteristic.uuid.toString()) {
                    "00002a2b-0000-1000-8000-00805f9b34fb" -> currentTimeChar   = caracteristic
                    "3c0a1001-281d-4b48-b2a7-f15579a1c38f" -> integerChar       = caracteristic
                    "3c0a1002-281d-4b48-b2a7-f15579a1c38f" -> temperatureChar   = caracteristic
                    "3c0a1003-281d-4b48-b2a7-f15579a1c38f" -> buttonClickChar   = caracteristic
                }
            }
        }

        /*
            Log.d("CHECK", timeService.toString())
            Log.d("CHECK", symService.toString())
            Log.d("CHECK", currentTimeChar.toString())
            Log.d("CHECK", integerChar.toString())
            Log.d("CHECK", temperatureChar.toString())
            Log.d("CHECK", buttonClickChar.toString())
        */

        // Si tout est OK, on retourne true
        // sinon la librairie appelle la méthode onDeviceDisconnected() avec le flag REASON_NOT_SUPPORTED
        return (timeService != null &&
                symService != null &&
                currentTimeChar != null &&
                integerChar != null &&
                temperatureChar != null &&
                buttonClickChar != null)
    }

    override fun initialize() {
        super.initialize()
        /*
            Ici nous somme sûr que le périphérique possède bien tous les services et caractéristiques
            attendus et que nous y sommes connectés. Nous pouvous effectuer les premiers échanges BLE.
            Dans notre cas il s'agit de s'enregistrer pour recevoir les notifications proposées par certaines
            caractéristiques, on en profitera aussi pour mettre en place les callbacks correspondants.
            CF. méthodes setNotificationCallback().with{} et enableNotifications().enqueue()
         */

        enableNotifications(currentTimeChar).enqueue()
        setNotificationCallback(currentTimeChar).with{_, data: Data ->
            val newCalendar = Calendar.getInstance()

            data.value?.let{
                val year = it[0].toInt() and 0xff or (it[1].toInt() and 0xff shl 8)

                newCalendar.set(year, it[2].toInt(), it[3].toInt(),
                    it[4].toInt(), it[5].toInt(), it[6].toInt())
            }

            Log.d("DATE", newCalendar.toString())
            dmaServiceListener?.dateUpdate(newCalendar)
        }

        enableNotifications(buttonClickChar).enqueue()
        setNotificationCallback(buttonClickChar).with{_, data: Data ->
            data.value?.let {
                var result = 0
                for (i in it.indices) {
                    result = result or (it[i].toInt() shl 8 * i)
                }
                result
            }?.let {
                Log.d("BUTTON", it.toString())
                dmaServiceListener?.clickCountUpdate(it)
            }
        }
    }

    override fun onServicesInvalidated() {
        super.onServicesInvalidated()
        //we reset services and characteristics
        timeService = null
        currentTimeChar = null
        symService = null
        integerChar = null
        temperatureChar = null
        buttonClickChar = null
    }

    fun readTemperature(): Boolean {
        /* TODO
            on peut effectuer ici la lecture de la caractéristique température
            la valeur récupérée sera envoyée à au ViewModel en utilisant le mécanisme
            du DMAServiceListener: Cf. temperatureUpdate()
                Cf. méthode readCharacteristic().with{}.enqueue()
            On placera des méthodes similaires pour les autres opérations
                Cf. méthode writeCharacteristic().enqueue()
        */
        temperatureChar?.let { characteristic ->
            readCharacteristic(characteristic)
                .with { _, data ->
                    data.value?.let {
                        val temperature = ((it[0].toInt() and 0xFF) or ((it[1].toInt() and 0xFF) shl 8)) / 10f

                        Log.d("TEMP", "$temperature C")

                        dmaServiceListener?.temperatureUpdate(temperature)
                    }
                }
                .enqueue()

            return true
        }

        return false
    }

    companion object {
        private val TAG = DMABleManager::class.java.simpleName
    }

}

interface DMAServiceListener {
    fun dateUpdate(date : Calendar)
    fun temperatureUpdate(temperature : Float)
    fun clickCountUpdate(clickCount : Int)
}
