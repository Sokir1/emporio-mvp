using System;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.Reflection;
using System.Threading.Tasks;
using System.Windows.Forms;

#if WEB
[assembly: AssemblyTitle("Abrir página web")]
[assembly: AssemblyDescription("Abre la página web de Emporio")]
#else
[assembly: AssemblyTitle("Emporio")]
[assembly: AssemblyDescription("Abre la aplicación de Emporio")]
#endif
[assembly: AssemblyCompany("Comercializadora Ángel")]
[assembly: AssemblyProduct("Emporio")]
[assembly: AssemblyVersion("1.3.0.0")]
[assembly: AssemblyFileVersion("1.3.0.0")]

internal static class Lanzador
{
    [STAThread]
    private static void Main()
    {
        Application.EnableVisualStyles();
        Application.SetCompatibleTextRenderingDefault(false);
        string system = Path.Combine(Application.StartupPath, "Sistema");
        try
        {
#if WEB
            if (!File.Exists(Path.Combine(system, "Emporio.ps1"))) throw new FileNotFoundException();
            Application.Run(new AbrirWeb(system));
#else
            string desktop = Path.Combine(system, "EmporioApp.exe");
            if (!File.Exists(desktop)) throw new FileNotFoundException();
            var start = new ProcessStartInfo(desktop);
            start.WorkingDirectory = system;
            start.UseShellExecute = false;
            Process.Start(start);
#endif
        }
        catch
        {
            Environment.ExitCode = 1;
            MessageBox.Show("Extrae la carpeta completa del ZIP antes de abrir Emporio.\n\nEmporio.exe y la carpeta Sistema deben quedar juntos.",
                "Abrir Emporio", MessageBoxButtons.OK, MessageBoxIcon.Information);
        }
    }
}

#if WEB
internal sealed class AbrirWeb : Form
{
    private readonly string system;

    internal AbrirWeb(string path)
    {
        system = path;
        Text = "Abrir página web · Emporio";
        Icon = Icon.ExtractAssociatedIcon(Application.ExecutablePath);
        StartPosition = FormStartPosition.CenterScreen;
        ClientSize = new Size(450, 150);
        FormBorderStyle = FormBorderStyle.FixedDialog;
        MaximizeBox = false;
        BackColor = Color.FromArgb(248, 248, 242);
        var text = new Label();
        text.Dock = DockStyle.Fill;
        text.TextAlign = ContentAlignment.MiddleCenter;
        text.Font = new Font("Segoe UI", 14F);
        text.ForeColor = Color.FromArgb(37, 72, 53);
        text.Text = "Abriendo la página web…\nEspera un momento.";
        Controls.Add(text);
        Shown += async delegate { await StartAsync(); };
    }

    private async Task StartAsync()
    {
        try
        {
            await Task.Run(delegate
            {
                var start = new ProcessStartInfo(Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.System),
                    "WindowsPowerShell", "v1.0", "powershell.exe"));
                start.Arguments = "-NoProfile -NonInteractive -ExecutionPolicy Bypass -File \"" + Path.Combine(system, "Emporio.ps1") + "\" -Accion Iniciar";
                start.WorkingDirectory = system;
                start.UseShellExecute = false;
                start.CreateNoWindow = true;
                using (var process = Process.Start(start))
                {
                    if (!process.WaitForExit(120000))
                    {
                        try { process.Kill(); } catch { }
                        throw new InvalidOperationException();
                    }
                    if (process.ExitCode != 0) throw new InvalidOperationException();
                }
            });
        }
        catch
        {
            Environment.ExitCode = 1;
            if (!IsDisposed)
                MessageBox.Show(this, "No fue posible abrir Emporio.\n\nCierra las otras instalaciones de Emporio y vuelve a intentarlo.\nSi el problema continúa, pide ayuda al encargado.",
                    "Abrir página web", MessageBoxButtons.OK, MessageBoxIcon.Information);
        }
        finally { if (!IsDisposed) Close(); }
    }
}
#endif
