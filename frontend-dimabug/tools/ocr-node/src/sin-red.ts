/** Preload exclusivo del prototipo: deniega las APIs de red antes de cargar librerias. */
import http = require('node:http');
import https = require('node:https');
import net = require('node:net');
import dgram = require('node:dgram');
import tls = require('node:tls');

const denegar = (): never => { throw new Error('El prototipo OCR no permite conexiones de red.'); };
for (const module of [http,https]) {
  Reflect.set(module,'request',denegar); Reflect.set(module,'get',denegar);
}
Reflect.set(net,'connect',denegar); Reflect.set(net,'createConnection',denegar);
Reflect.set(net.Socket.prototype,'connect',denegar);
Reflect.set(tls,'connect',denegar); Reflect.set(dgram,'createSocket',denegar);
Reflect.set(globalThis,'fetch',async (): Promise<never> => denegar());
