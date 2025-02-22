const reportWebVitals = (onPerfEntry) => {
    if (onPerfEntry && onPerfEntry instanceof Function) {
        import('web-vitals').then(({getCLS, getFID, getFCP, getLCP, getTTFB}) => {
            // Core Web Vitals
            getCLS(onPerfEntry);
            getFID(onPerfEntry);
            getFCP(onPerfEntry);
            getLCP(onPerfEntry);
            getTTFB(onPerfEntry);
        }).catch(error => {
            console.error('Web-vitals initialization failed:', error.message);
        });
    } else {
       console.warn('Web-vitals initialization failed: onPerfEntry must be a valid function');
    }
};

export default reportWebVitals;