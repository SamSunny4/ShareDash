using System.IO;
using System.Windows;

namespace ShareDash.Windows;

public partial class App : Application
{
    protected override void OnStartup(StartupEventArgs e)
    {
        string logPath = @"E:\ShareDash\app_debug.log";
        File.WriteAllText(logPath, $"OnStartup at {DateTime.Now}\n");

        AppDomain.CurrentDomain.UnhandledException += (s, args) =>
        {
            File.AppendAllText(logPath, $"Unhandled: {args.ExceptionObject}\n");
        };
        DispatcherUnhandledException += (s, args) =>
        {
            File.AppendAllText(logPath, $"Dispatcher: {args.Exception}\n");
            args.Handled = true;
        };

        try
        {
            base.OnStartup(e);
            File.AppendAllText(logPath, "Base OnStartup completed\n");
        }
        catch (Exception ex)
        {
            File.AppendAllText(logPath, $"OnStartup exception: {ex}\n");
        }
    }
}
