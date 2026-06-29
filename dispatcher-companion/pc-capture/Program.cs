using NAudio.CoreAudioApi;
using NAudio.Wave;

// Dispatcher Companion — Windows dual-stream capture (PoC).
//
// THE IDEA (why PC is more reliable than Android):
//   * Your voice goes INTO the headset mic   -> WasapiCapture (a capture device)
//   * The broker's voice comes OUT to the headphones -> WasapiLoopbackCapture
//     (it records whatever is rendered to the output device, including headphones)
// Two physically separate streams = perfect speaker separation, no diarization
// guessing. Mic = DISPATCHER, loopback = BROKER.
//
// This proof records both streams to two 16 kHz mono WAV files. Step 2 (see
// README) transcribes each with whisper.cpp and pipes labelled lines into the
// JVM engine (dispatcher-desktop) which extracts PDWCR + negotiation.
//
// Build:  dotnet build -c Release
// Run:    DispatcherCapture            (records to .\broker.wav and .\dispatcher.wav)
//         DispatcherCapture --list     (list audio devices to pick a specific headset)

internal static class Program
{
    private static int Main(string[] args)
    {
        var enumerator = new MMDeviceEnumerator();

        if (args.Contains("--list"))
        {
            Console.WriteLine("== Render (output) devices — broker audio plays here ==");
            foreach (var d in enumerator.EnumerateAudioEndPoints(DataFlow.Render, DeviceState.Active))
                Console.WriteLine($"  {d.FriendlyName}");
            Console.WriteLine("== Capture (input) devices — your headset mic ==");
            foreach (var d in enumerator.EnumerateAudioEndPoints(DataFlow.Capture, DeviceState.Active))
                Console.WriteLine($"  {d.FriendlyName}");
            return 0;
        }

        // Default devices: the headphones you hear the broker in, and the headset mic.
        // To target a specific headset, swap these for a device from --list.
        var renderDevice = enumerator.GetDefaultAudioEndpoint(DataFlow.Render, Role.Communications);
        var captureDevice = enumerator.GetDefaultAudioEndpoint(DataFlow.Capture, Role.Communications);

        Console.WriteLine($"Broker  (loopback) <- {renderDevice.FriendlyName}");
        Console.WriteLine($"You     (mic)      <- {captureDevice.FriendlyName}");
        Console.WriteLine("Recording… press ENTER to stop.");

        // 16 kHz mono PCM is exactly what whisper.cpp / Vosk want.
        var target = new WaveFormat(16000, 16, 1);

        using var brokerCapture = new WasapiLoopbackCapture(renderDevice);
        using var youCapture = new WasapiCapture(captureDevice);

        using var brokerWriter = new ResamplingWaveWriter("broker.wav", brokerCapture.WaveFormat, target);
        using var youWriter = new ResamplingWaveWriter("dispatcher.wav", youCapture.WaveFormat, target);

        brokerCapture.DataAvailable += (_, e) => brokerWriter.Write(e.Buffer, e.BytesRecorded);
        youCapture.DataAvailable += (_, e) => youWriter.Write(e.Buffer, e.BytesRecorded);

        brokerCapture.StartRecording();
        youCapture.StartRecording();
        Console.ReadLine();
        brokerCapture.StopRecording();
        youCapture.StopRecording();

        Console.WriteLine("Saved broker.wav and dispatcher.wav.");
        Console.WriteLine("Next: transcribe each with whisper.cpp and pipe labelled lines into dispatcher-desktop (see README).");
        return 0;

        // NOTE — isolating ONLY RingCentral's audio (the reliable upgrade):
        // default-device loopback also captures music/notifications. Windows 10
        // 2004+ has the Application Loopback API (ActivateAudioInterfaceAsync with
        // AUDIOCLIENT_ACTIVATION_PARAMS + PROCESS_LOOPBACK) to capture a single
        // process. See README → "Per-process loopback". For the PoC, just keep
        // other audio quiet during the call.
    }
}

/// <summary>Writes capture buffers as a WAV file, resampling to the target format.</summary>
internal sealed class ResamplingWaveWriter : IDisposable
{
    private readonly BufferedWaveProvider _buffer;
    private readonly MediaFoundationResampler _resampler;
    private readonly WaveFileWriter _writer;
    private readonly byte[] _scratch = new byte[16384];

    public ResamplingWaveWriter(string path, WaveFormat source, WaveFormat target)
    {
        _buffer = new BufferedWaveProvider(source) { DiscardOnBufferOverflow = true, BufferDuration = TimeSpan.FromSeconds(10) };
        _resampler = new MediaFoundationResampler(_buffer, target) { ResamplerQuality = 60 };
        _writer = new WaveFileWriter(path, target);
    }

    public void Write(byte[] data, int bytes)
    {
        _buffer.AddSamples(data, 0, bytes);
        int read;
        while ((read = _resampler.Read(_scratch, 0, _scratch.Length)) > 0)
            _writer.Write(_scratch, 0, read);
    }

    public void Dispose()
    {
        _writer.Dispose();
        _resampler.Dispose();
    }
}
