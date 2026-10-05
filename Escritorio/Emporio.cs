using System;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.Reflection;
using System.Runtime.InteropServices;
using System.Security.Cryptography;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using System.Web.Script.Serialization;
using System.Windows.Forms;
using Microsoft.Web.WebView2.Core;
using Microsoft.Web.WebView2.WinForms;

[assembly: AssemblyTitle("Emporio")]
[assembly: AssemblyDescription("Ventas, inventario y bodega de Comercializadora Ángel")]
[assembly: AssemblyCompany("Comercializadora Ángel")]
[assembly: AssemblyProduct("Emporio")]
[assembly: AssemblyVersion("1.3.0.0")]
[assembly: AssemblyFileVersion("1.3.0.0")]

internal static class Program
{
    [DllImport("user32.dll")]
    private static extern bool SetForegroundWindow(IntPtr window);
    [DllImport("user32.dll")]
    private static extern bool ShowWindow(IntPtr window, int command);

    [STAThread]
    private static void Main()
    {
        Application.EnableVisualStyles();
        Application.SetCompatibleTextRenderingDefault(false);
        string key;
        using (var hash = SHA256.Create())
            key = BitConverter.ToString(hash.ComputeHash(Encoding.UTF8.GetBytes(Application.StartupPath.ToLowerInvariant()))).Replace("-", "");
        using (var mutex = new Mutex(false, "Local\\Emporio-Escritorio-" + key))
        {
            bool owned;
            try { owned = mutex.WaitOne(0); }
            catch (AbandonedMutexException) { owned = true; }
            if (!owned)
            {
                foreach (var process in Process.GetProcessesByName(Path.GetFileNameWithoutExtension(Application.ExecutablePath)))
                {
                    try
                    {
                        if (process.Id != Process.GetCurrentProcess().Id &&
                            String.Equals(process.MainModule.FileName, Application.ExecutablePath, StringComparison.OrdinalIgnoreCase))
                        {
                            ShowWindow(process.MainWindowHandle, 9);
                            SetForegroundWindow(process.MainWindowHandle);
                            break;
                        }
                    }
                    catch { }
                    finally { process.Dispose(); }
                }
                return;
            }
            try { Application.Run(new EmporioWindow()); }
            finally { mutex.ReleaseMutex(); }
        }
    }
}

internal sealed class EmporioSettings
{
    public int appPort { get; set; }
}

internal sealed class EmporioWindow : Form
{
    private readonly string root = Application.StartupPath;
    private readonly WebView2 view = new WebView2();
    private readonly Label loading = new Label();
    private readonly ToolStripStatusLabel status = new ToolStripStatusLabel();
    private readonly MenuStrip menu = new MenuStrip();
    private Uri home;
    private bool ready;
    private bool busy;

    internal EmporioWindow()
    {
        Text = "Emporio · Comercializadora Ángel";
        Icon = Icon.ExtractAssociatedIcon(Application.ExecutablePath);
        StartPosition = FormStartPosition.CenterScreen;
        Size = new Size(1360, 900);
        MinimumSize = new Size(900, 650);
        AutoScaleMode = AutoScaleMode.Dpi;
        Font = new Font("Segoe UI", 10F);
        BackColor = Color.FromArgb(248, 248, 242);

        menu.BackColor = Color.FromArgb(37, 72, 53);
        menu.ForeColor = Color.White;
        menu.Padding = new Padding(12, 7, 12, 7);
        menu.Font = new Font("Segoe UI", 10F);
        var brand = new ToolStripMenuItem("EMPORIO");
        brand.Font = new Font("Segoe UI", 11F, FontStyle.Bold);
        brand.Click += delegate { if (ready) view.CoreWebView2.Navigate(home.AbsoluteUri); };
        menu.Items.Add(brand);
        var system = new ToolStripMenuItem("Opciones");
        system.Alignment = ToolStripItemAlignment.Right;
        system.DropDownItems.Add("Abrir página web", null, delegate { OpenWeb(); });
        system.DropDownItems.Add("Guardar copia de seguridad", null, async delegate { await BackupAsync(); });
        system.DropDownItems.Add("Recargar pantalla", null, delegate { if (ready) view.CoreWebView2.Reload(); });
        system.DropDownItems.Add(new ToolStripSeparator());
        system.DropDownItems.Add("Cerrar ventana", null, delegate { Close(); });
        system.DropDownItems.Add("Apagar Emporio", null, async delegate { await ShutdownAsync(); });
        menu.Items.Add(system);
        MainMenuStrip = menu;

        var statusBar = new StatusStrip();
        statusBar.BackColor = Color.FromArgb(236, 240, 232);
        statusBar.SizingGrip = false;
        status.Spring = true;
        status.TextAlign = ContentAlignment.MiddleLeft;
        status.Text = "Preparando Emporio…";
        statusBar.Items.Add(status);
        loading.Dock = DockStyle.Fill;
        loading.TextAlign = ContentAlignment.MiddleCenter;
        loading.Font = new Font("Segoe UI", 19F);
        loading.ForeColor = Color.FromArgb(37, 72, 53);
        loading.Text = "Emporio\n\nIniciando tu negocio…";
        view.Dock = DockStyle.Fill;
        view.Visible = false;
        view.DefaultBackgroundColor = BackColor;
        var content = new Panel();
        content.Dock = DockStyle.Fill;
        content.Margin = Padding.Empty;
        content.Controls.Add(loading);
        content.Controls.Add(view);

        var layout = new TableLayoutPanel();
        layout.Dock = DockStyle.Fill;
        layout.Margin = Padding.Empty;
        layout.Padding = Padding.Empty;
        layout.ColumnCount = 1;
        layout.RowCount = 3;
        layout.GrowStyle = TableLayoutPanelGrowStyle.FixedSize;
        layout.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 100F));
        layout.RowStyles.Add(new RowStyle(SizeType.AutoSize));
        layout.RowStyles.Add(new RowStyle(SizeType.Percent, 100F));
        layout.RowStyles.Add(new RowStyle(SizeType.AutoSize));
        menu.Dock = DockStyle.Fill;
        menu.Margin = Padding.Empty;
        statusBar.Dock = DockStyle.Fill;
        statusBar.Margin = Padding.Empty;
        layout.Controls.Add(menu, 0, 0);
        layout.Controls.Add(content, 0, 1);
        layout.Controls.Add(statusBar, 0, 2);
        Controls.Add(layout);
        Shown += async delegate { await StartAsync(); };
        FormClosed += delegate { view.Dispose(); };
    }

    private async Task StartAsync()
    {
        busy = true;
        try
        {
            CoreWebView2Environment.SetLoaderDllFolderPath(Path.Combine(root, "escritorio"));
            await EnsureRuntimeAsync();
            if (IsDisposed) return;
            await RunServiceAsync("Iniciar");
            if (IsDisposed) return;
            var settings = new JavaScriptSerializer().Deserialize<EmporioSettings>(
                File.ReadAllText(Path.Combine(root, "datos", "configuracion.json")));
            if (settings.appPort < 1024 || settings.appPort > 65535)
                throw new InvalidOperationException("El puerto de Emporio no es válido.");
            home = new Uri("http://127.0.0.1:" + settings.appPort + "/");
            status.Text = "Abriendo Emporio…";
            var environment = await CoreWebView2Environment.CreateAsync(null, Path.Combine(root, "datos", "escritorio"));
            if (IsDisposed) return;
            await view.EnsureCoreWebView2Async(environment);
            var browser = view.CoreWebView2;
            browser.Settings.AreDevToolsEnabled = false;
            browser.Settings.AreDefaultContextMenusEnabled = false;
            browser.Settings.IsStatusBarEnabled = false;
            browser.Settings.IsPasswordAutosaveEnabled = false;
            browser.Settings.IsGeneralAutofillEnabled = false;
            browser.Settings.AreHostObjectsAllowed = false;
            browser.Settings.IsWebMessageEnabled = false;
            browser.PermissionRequested += delegate(object sender, CoreWebView2PermissionRequestedEventArgs e)
            {
                e.State = CoreWebView2PermissionState.Deny;
                e.Handled = true;
            };
            browser.NavigationStarting += delegate(object sender, CoreWebView2NavigationStartingEventArgs e)
            {
                if (!IsLocalPage(e.Uri)) e.Cancel = true;
            };
            browser.NewWindowRequested += delegate(object sender, CoreWebView2NewWindowRequestedEventArgs e)
            {
                e.Handled = true;
                if (IsLocalPage(e.Uri)) browser.Navigate(e.Uri);
            };
            browser.NavigationCompleted += delegate(object sender, CoreWebView2NavigationCompletedEventArgs e)
            {
                if (IsDisposed) return;
                if (e.IsSuccess)
                {
                    ready = true;
                    loading.Visible = false;
                    view.Visible = true;
                    status.Text = "Conectado · Ventas, inventario y bodega";
                }
                else if (e.WebErrorStatus != CoreWebView2WebErrorStatus.OperationCanceled)
                {
                    status.Text = "No se pudo abrir la vista. Vuelve a abrir Emporio.";
                }
            };
            browser.DownloadStarting += SaveDownload;
            browser.ProcessFailed += delegate
            {
                ready = false;
                status.Text = "La ventana necesita reiniciarse. Cierra la aplicación y vuelve a abrirla.";
            };
            browser.Navigate(home.AbsoluteUri);
        }
        catch (Exception error)
        {
            if (!IsDisposed)
            {
                SaveError(error);
                loading.Text = "No fue posible abrir Emporio.\n\nCierra la aplicación y vuelve a abrirla.\nSi el problema continúa, pide ayuda al encargado.";
                status.Text = "Emporio no pudo iniciar.";
            }
        }
        finally { busy = false; }
    }

    private bool IsLocalPage(string address)
    {
        Uri candidate;
        return home != null && Uri.TryCreate(address, UriKind.Absolute, out candidate) &&
               candidate.Scheme == home.Scheme && candidate.Host == home.Host && candidate.Port == home.Port;
    }

    private async Task EnsureRuntimeAsync()
    {
        try
        {
            if (!String.IsNullOrEmpty(CoreWebView2Environment.GetAvailableBrowserVersionString())) return;
        }
        catch (WebView2RuntimeNotFoundException) { }
        loading.Text = "Emporio\n\nPreparando la aplicación por primera vez…\nNecesita Internet solo para esta preparación.";
        status.Text = "Preparando el componente de la ventana…";
        string installer = Path.Combine(root, "runtime", "MicrosoftEdgeWebview2Setup.exe");
        if (!File.Exists(installer))
            throw new InvalidOperationException("Falta un componente de la aplicación. Extrae nuevamente el paquete completo.");
        await Task.Run(delegate
        {
            var start = new ProcessStartInfo(installer, "/silent /install");
            start.UseShellExecute = false;
            start.CreateNoWindow = true;
            using (var process = Process.Start(start))
            {
                if (!process.WaitForExit(240000))
                    throw new InvalidOperationException("El componente de la ventana está tardando en instalarse. Vuelve a abrir Emporio.");
            }
        });
        if (String.IsNullOrEmpty(CoreWebView2Environment.GetAvailableBrowserVersionString()))
            throw new InvalidOperationException("No se pudo preparar la ventana de Emporio. Consulta con quien administra este computador.");
    }

    private Task<string> RunServiceAsync(string action)
    {
        return Task.Run(delegate
        {
            var script = Path.Combine(root, "Emporio.ps1");
            if (!File.Exists(script))
                throw new InvalidOperationException("Faltan archivos de Emporio. Extrae el paquete completo antes de abrirlo.");
            var start = new ProcessStartInfo(Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.System),
                "WindowsPowerShell", "v1.0", "powershell.exe"));
            start.Arguments = "-NoProfile -NonInteractive -ExecutionPolicy Bypass -File \"" + script + "\" -Accion " + action + " -SinAbrir";
            start.WorkingDirectory = root;
            start.UseShellExecute = false;
            start.CreateNoWindow = true;
            using (var process = Process.Start(start))
            {
                if (!process.WaitForExit(120000))
                {
                    try { process.Kill(); } catch { }
                    throw new InvalidOperationException("El sistema está tardando en responder. Espera un momento y vuelve a abrir Emporio.");
                }
                if (process.ExitCode != 0)
                {
                    Directory.CreateDirectory(Path.Combine(root, "datos"));
                    File.AppendAllText(Path.Combine(root, "datos", "escritorio-error.log"),
                        DateTime.Now.ToString("s") + " Servicio " + action + ": " + process.ExitCode + "\n");
                    throw new InvalidOperationException("No se pudo iniciar o atender el sistema. Verifica que no haya otra instalación abierta y vuelve a intentarlo.");
                }
                return "Emporio";
            }
        });
    }

    private void OpenWeb()
    {
        if (ready) Process.Start(home.AbsoluteUri);
    }

    private async Task BackupAsync()
    {
        if (!ready || busy) return;
        busy = true;
        status.Text = "Guardando respaldo…";
        try
        {
            await RunServiceAsync("Respaldar");
            if (!IsDisposed)
                MessageBox.Show(this, "La copia se guardó en la carpeta Sistema\\respaldos de Emporio.\n\nConserva otra copia fuera de este computador.",
                    "Respaldo guardado", MessageBoxButtons.OK, MessageBoxIcon.Information);
        }
        catch (Exception error)
        {
            if (!IsDisposed) MessageBox.Show(this, error.Message, "No se pudo guardar el respaldo", MessageBoxButtons.OK, MessageBoxIcon.Error);
        }
        finally
        {
            busy = false;
            if (!IsDisposed) status.Text = "Conectado · Ventas, inventario y bodega";
        }
    }

    private async Task ShutdownAsync()
    {
        if (!ready || busy) return;
        if (MessageBox.Show(this, "Se cerrará Emporio en esta aplicación y en la página web.\n\n¿Quieres apagar el sistema?",
            "Apagar Emporio", MessageBoxButtons.YesNo, MessageBoxIcon.Question) != DialogResult.Yes) return;
        busy = true;
        status.Text = "Apagando Emporio…";
        try
        {
            await RunServiceAsync("Respaldar");
            await RunServiceAsync("Detener");
            Close();
        }
        catch (Exception error)
        {
            busy = false;
            status.Text = "No se pudo apagar el sistema.";
            if (!IsDisposed) MessageBox.Show(this, error.Message, "Emporio", MessageBoxButtons.OK, MessageBoxIcon.Error);
        }
    }

    private void SaveDownload(object sender, CoreWebView2DownloadStartingEventArgs e)
    {
        var pause = e.GetDeferral();
        try
        {
            using (var dialog = new SaveFileDialog())
            {
                dialog.Title = "Guardar archivo de Emporio";
                dialog.FileName = Path.GetFileName(e.ResultFilePath);
                dialog.InitialDirectory = Environment.GetFolderPath(Environment.SpecialFolder.MyDocuments);
                dialog.Filter = "Archivo CSV (*.csv)|*.csv|Todos los archivos (*.*)|*.*";
                dialog.OverwritePrompt = true;
                e.Handled = true;
                if (dialog.ShowDialog(this) == DialogResult.OK) e.ResultFilePath = dialog.FileName;
                else e.Cancel = true;
            }
        }
        finally { pause.Complete(); }
    }

    private void SaveError(Exception error)
    {
        try
        {
            Directory.CreateDirectory(Path.Combine(root, "datos"));
            File.AppendAllText(Path.Combine(root, "datos", "escritorio-error.log"), DateTime.Now.ToString("s") + "\n" + error + "\n");
        }
        catch { }
    }
}
