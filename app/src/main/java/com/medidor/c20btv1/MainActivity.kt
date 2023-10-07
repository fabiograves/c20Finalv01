package com.medidor.c20btv1

import androidx.appcompat.app.AppCompatActivity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import androidx.core.app.ActivityCompat
import java.io.IOException
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private lateinit var textViewPesoBt: TextView
    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var bluetoothSocket: BluetoothSocket? = null
    private val REQUEST_BLUETOOTH_PERMISSIONS = 1


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // ###################### VARIAVEIS ###########################
        // Obtém as variaveis
        val textViewLog = findViewById<TextView>(R.id.textViewLog)
        // Obtém o botão buttonConectarBt
        val buttonConectarBt = findViewById<Button>(R.id.buttonConectarBt)

        // Inicialize as vistas
        textViewPesoBt = findViewById(R.id.textViewPesoBt)
        // Limita o tamanho do texto do textViewPesoBt a uma linha
        textViewPesoBt.maxLines = 1
        // ####################### VARIAVEIS ############################

        // ############################ TEXT LOG #############################
        // Atualiza o texto do textViewLog
        fun atualizarLog(status: String) {
            runOnUiThread {
                // Adiciona um delay de 1 segundo
                Thread.sleep(1000)

                // Obtém o item selecionado no spinner
                val itemSelecionado = findViewById<Spinner>(R.id.spinnerBt).selectedItem as String

                // Verifica se o bluetoothSocket existe e está conectado
                if (bluetoothSocket == null || !bluetoothSocket!!.isConnected) {
                    // O Bluetooth não está conectado, exiba o texto "Desconectado"
                    textViewLog.text = "Desconectado"
                } else {
                    // O Bluetooth está conectado, exiba o texto "Conectado ao ${itemSelecionado}"
                    textViewLog.text = "Conectado ao ${itemSelecionado}"
                }
            }
        }

        // ############################ /TEXT LOG #############################

        // ##################### BLUETOOTH ###############################
        // Verifique as permissões e inicie o Bluetooth
        checkAndInitializeBluetooth()

        // Inicie a descoberta de dispositivos Bluetooth
        startBluetoothDiscovery()

        // Inicializa o Bluetooth Manager e Adapter
        bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        // Obtém a lista de dispositivos pareados
        val devices = BluetoothAdapter.getDefaultAdapter().getBondedDevices()

        // Função para desconectar o Bluetooth
        fun desconectarBluetooth() {
            // Desconecta o BluetoothSocket
            bluetoothSocket?.close()

            // Altera o texto do botão buttonConectarBt para Conectar
            buttonConectarBt.text = "Conectar"


            // Atualiza o texto do textViewLog
            atualizarLog("Desconectado")
        }

        // ##################### /BLUETOOTH ###############################


        // ############################# SPINNER #############################
        // Cria um adaptador para o spinner
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            devices.map { it.name }
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        // Vincula o spinner ao adaptador
        findViewById<Spinner>(R.id.spinnerBt).adapter = adapter

        // ############################ /SPINNER #############################


        // ############################ BUTTON CONECTAR #######################

        // Vincula o onclick ao botão
        buttonConectarBt.setOnClickListener {
            // Obtém o item selecionado no spinner
            val itemSelecionado = findViewById<Spinner>(R.id.spinnerBt).selectedItem as String

            // Obtém o objeto BluetoothDevice correspondente ao item selecionado no spinner
            val dispositivoSelecionado = devices.find { it.name == itemSelecionado }

            // Verifica se o bluetoothSocket existe e está conectado antes de tentar conectar novamente
            if (bluetoothSocket == null || !bluetoothSocket!!.isConnected) {
                // O Bluetooth não está conectado, conecta ao dispositivo selecionado
                // Use um operador safe call para verificar se dispositivoSelecionado é nulo
                bluetoothSocket = dispositivoSelecionado?.createRfcommSocketToServiceRecord(
                    UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
                )

                try {
                    bluetoothSocket!!.connect()

                    // Inicia uma thread para receber os dados do Bluetooth
                    Thread {
                        val inputStream = bluetoothSocket!!.inputStream
                        val buffer = ByteArray(1024)
                        val valoresRecebidos = mutableListOf<String>()

                        while (true) {
                            try {
                                val bytes = inputStream.read(buffer)
                                val data = String(buffer, 0, bytes).trim()
                                val dataWithoutDot = data.trimEnd('.')

                                valoresRecebidos.add(dataWithoutDot)
                                if (valoresRecebidos.size > 30) {
                                    valoresRecebidos.removeAt(0)
                                }

                                val valorMaisFrequente =
                                    valoresRecebidos.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key

                                // Atualiza o texto do textViewPesoBt com um delay de 1 segundo
                                runOnUiThread {
                                    textViewPesoBt.text = valorMaisFrequente ?: ""
                                }

                                // Adiciona um delay de 1 segundo
                                //Thread.sleep(1000)
                            } catch (e: IOException) {
                                e.printStackTrace()
                            }
                        }
                    }.start()

                    // Altera o texto do botão buttonConectarBt para Desconectar
                    buttonConectarBt.text = "Desconectar"
                    // O Bluetooth está conectado, exiba o texto "Conectado ao ${itemSelecionado}"
                    textViewLog.text = "Conectado ao ${itemSelecionado!!}"
                } catch (e: IOException) {
                    e.printStackTrace()
                    // Lida com a exceção de conexão Bluetooth aqui
                    textViewLog.text = "Não foi possível se conectar ao ${itemSelecionado!!}"
                }
            } else {
                // O Bluetooth já está conectado, desconecta o Bluetooth
                desconectarBluetooth()
            }
        }

        // ######################### /BUTTON CONECTAR ######################################

    }

    // ############################ FUNCOES BLUETOOTH ###################################
    private fun checkAndInitializeBluetooth() {
        // Verifique as permissões e inicialize o Bluetooth
        if (checkBluetoothPermissions()) {
            initializeBluetooth()
        } else {
            requestBluetoothPermissions()
        }
    }

    private fun checkBluetoothPermissions(): Boolean {
        // Verifique se as permissões BLUETOOTH_ADMIN e ACCESS_FINE_LOCATION foram concedidas
        if (ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.BLUETOOTH_CONNECT), 2)
            return false
        }
        return true
    }

    private fun requestBluetoothPermissions() {
        // Solicite as permissões BLUETOOTH_ADMIN e ACCESS_FINE_LOCATION e BLUETOOTH_CONNECTION ao usuário

    }

    private fun initializeBluetooth() {
        // Inicialize o Bluetooth aqui
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        // Verifique se o Bluetooth está disponível e habilite-o se não estiver

        // ... (outros inicializações relacionadas ao Bluetooth)
    }

    // Função para verificar se a conexão Bluetooth está ativa
    private fun isBluetoothConnected(): Boolean {
        return bluetoothSocket != null && bluetoothSocket!!.isConnected
    }

    private fun startBluetoothDiscovery() {
        // Inicie a descoberta de dispositivos Bluetooth aqui
        if (ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        bluetoothAdapter.startDiscovery()

        // Registre o BroadcastReceiver para lidar com dispositivos encontrados
        registerBluetoothDiscoveryReceiver()
    }

    private fun registerBluetoothDiscoveryReceiver() {
        // Registre o BroadcastReceiver para lidar com dispositivos encontrados
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val action = intent.action

                if (action == BluetoothDevice.ACTION_FOUND) {
                    // Trate os dispositivos encontrados e atualize a lista
                    handleDiscoveredDevice(intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE))
                }
            }
        }

        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        registerReceiver(receiver, filter)
    }

    private fun handleDiscoveredDevice(device: BluetoothDevice?) {
        // Trate os dispositivos Bluetooth encontrados e atualize a lista no Spinner
    }

    // Adicione outras funções relacionadas à conexão Bluetooth e desconexão aqui

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                initializeBluetooth()
            } else {
                // Trate o caso em que o usuário recusou permissões
            }
        }
    }
    // ############################ /FUNCOES BLUETOOTH ###################################
}