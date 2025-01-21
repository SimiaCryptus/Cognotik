const reportWebVitals = (onPerfEntry) => {
    if (onPerfEntry && onPerfEntry instanceof Function) {
        import('web-vitals').then(({getCLS, getFID, getFCP, getLCP, getTTFB}) => {
            // Core Web Vitals
            getCLS(onPerfEntry);
            getFID(onPerfEntry);
            getFCP(onPerfEntry);
            getLCP(onPerfEntry);
            getTTFB(onPerfEntry);
            console.debug('Web vitals metrics initialized successfully');
        }).catch(error => {
            console.error('Web-vitals initialization failed:', error.message);
        });
    } else {
        console.warn('Invalid performance metrics handler provided');
    }
};

export default reportWebVitals;