 type LogLevel = 'debug' | 'info' | 'warn' | 'error';
 
 interface LoggerOptions {
   prefix?: string;
   level?: LogLevel;
 }
 
 class Logger {
   private prefix: string;
   private level: LogLevel;
 
   constructor(options: LoggerOptions = {}) {
     this.prefix = options.prefix || '';
     this.level = options.level || 'info';
   }
 
   debug(message: string, ...args: any[]) {
     if (process.env.NODE_ENV === 'development') {
       console.debug(`${this.prefix} ${message}`, ...args);
     }
   }
 
   info(message: string, ...args: any[]) {
     console.info(`${this.prefix} ${message}`, ...args);
   }
 
   warn(message: string, ...args: any[]) {
     console.warn(`${this.prefix} ${message}`, ...args);
   }
 
   error(message: string, ...args: any[]) {
     console.error(`${this.prefix} ${message}`, ...args);
   }
 }
 
 export const logger = new Logger({ prefix: '[App]' });