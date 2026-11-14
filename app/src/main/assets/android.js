(() => {
    const android = window.RechenzauberAndroid;
    if (!android) return;

    window.print = () => {
        const title = document.title;
        if (!document.querySelector("#sheet .a4")) return;
        window.dispatchEvent(new Event("beforeprint"));
        android.printWorksheet(title);
    };

    window.rzAndroidSavePdf = (blob, filename) => new Promise((resolve, reject) => {
        if (blob.size > 32 * 1024 * 1024) {
            if (typeof window.rzToast === "function") {
                window.rzToast("Die PDF ist zu groß. Bitte speichere weniger Seiten auf einmal.");
            }
            resolve();
            return;
        }
        const reader = new FileReader();
        reader.onerror = () => reject(reader.error);
        reader.onload = () => {
            android.savePdf(filename, reader.result.slice(reader.result.indexOf(",") + 1));
            resolve();
        };
        reader.readAsDataURL(blob);
    });
})();
