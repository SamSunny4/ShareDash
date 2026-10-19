using Windows.Devices.Radios;

namespace ShareDash.Windows.Services;

public class BluetoothRadioService
{
    public static async Task<bool> IsBluetoothOnAsync()
    {
        try
        {
            var radios = await Radio.GetRadiosAsync();
            var bt = radios.FirstOrDefault(r => r.Kind == RadioKind.Bluetooth);
            return bt != null && bt.State == RadioState.On;
        }
        catch
        {
            // Fallback check
            return false;
        }
    }

    public static async Task<bool> TurnOnBluetoothAsync()
    {
        try
        {
            var radios = await Radio.GetRadiosAsync();
            var bt = radios.FirstOrDefault(r => r.Kind == RadioKind.Bluetooth);
            if (bt != null)
            {
                var result = await bt.SetStateAsync(RadioState.On);
                return result == RadioAccessStatus.Allowed;
            }
        }
        catch { }
        return false;
    }
}
