!function(t){var e={};function n(r){if(e[r])return e[r].exports;var o=e[r]={i:r,l:!1,exports:{}};return t[r].call(o.exports,o,o.exports,n),o.l=!0,o.exports}n.m=t,n.c=e,n.d=function(t,e,r){n.o(t,e)||Object.defineProperty(t,e,{enumerable:!0,get:r})},n.r=function(t){'undefined'!=typeof Symbol&&Symbol.toStringTag&&Object.defineProperty(t,Symbol.toStringTag,{value:'Module'}),Object.defineProperty(t,'__esModule',{value:!0})},n.t=function(t,e){if(1&e&&(t=n(t)),8&e)return t;if(4&e&&'object'==typeof t&&t&&t.__esModule)return t;var r=Object.create(null);if(n.r(r),Object.defineProperty(r,'default',{enumerable:!0,value:t}),2&e&&'string'!=typeof t)for(var o in t)n.d(r,o,function(e){return t[e]}.bind(null,o));return r},n.n=function(t){var e=t&&t.__esModule?function(){return t.default}:function(){return t};return n.d(e,'a',e),e},n.o=function(t,e){return Object.prototype.hasOwnProperty.call(t,e)},n.p='',n(n.s=6)}([function(t,e){e.OPERATIONS={CLEANUP:'cleanup',VALIDATE:'validate',PING:'ping'},e.PING_MESSAGE='OK',e.CUSTOM_COMPONENTS_FOLDER_NAME='custom-components',e.EOT=''},function(t,e,n){const{EOT:r}=n(0),o=n(2);let s;class c{constructor(){if(s)return s;s=this}static getInstance(){return s||new c}send(t){o.stdout.write(t)}sendError(t){o.stderr.write(t)}finally(){o.stdout.write(r),o.stderr.write(r)}}t.exports=c},function(t,e){t.exports=require('process')},function(t,e){t.exports=class{execute(){return Promise.resolve()}}},function(t,e){const n=['datagrid'];function r(t,e){if(Array.isArray(t))t.forEach(t=>r(t,e));else if(null!==t&&'object'==typeof t)if(t.tree&&Array.isArray(t.components)){const o=function(t,e,r){return n.includes(t)?(r[e]=[{}],r[e][0]):(r[e]={},r[e])}(t.type,t.key,e);Array.isArray(o)?(o.push({}),t.components.forEach((t,e)=>{r(t,o[0])})):t.components.forEach(t=>r(t,o))}else if(t.input)!function(t,e){Array.isArray(e)?e.push({[t]:!0}):'object'==typeof e&&(e[t]=!0)}(t.key,e);else for(let n in t)'object'==typeof t[n]&&r(t[n],e)}function o(t,e){if('object'==typeof t&&null!==t&&'object'==typeof e&&null!==e)if(Array.isArray(t))Array.isArray(e)||t.splice(0,t.length),function(t){const e=t.filter(t=>'object'==typeof t&&null!==t);t.splice(0,t.length,...e)}(t),function(t,e){const n=t.filter(t=>{const n=Object.keys(t);return e.some(t=>{return e=Object.keys(t),n.every(t=>e.includes(t));var e})});n.forEach((t,n)=>{'object'==typeof t&&o(t,e[n])}),t.splice(0,t.length,...n)}(t,e);else for(let n in t)n in e||delete t[n],null!==e[n]&&'object'==typeof e[n]&&('object'!=typeof t[n]||null===t[n]?delete t[n]:o(t[n],e[n]))}t.exports=function(t,e={}){const n=e.data,s={};return r(t,s),o(n,s),{...e,data:n}}},function(t,e){t.exports=require('formiojs')},function(t,e,n){n(7);const r=n(11),o=n(2),s=n(1),{EOT:c}=n(0),i=s.getInstance();var u='';function a(){const t=JSON.parse(u);u='',r(t.operation,t).execute().then(()=>{i.finally()})}o.stdin.on('data',(function(t){const e=t.indexOf(c);-1!==e?(u+=t.substring(0,e),a()):u+=t})).on('end',()=>{a()})},function(t,e,n){n(8),n(2).stdin.setEncoding('utf8')},function(t,e,n){(function(t){n(10)(void 0,{url:'http://localhost'}),t.Option=t.window.Option,t.window.matchMedia=function(t){return{matches:!1,media:t}}}).call(this,n(9))},function(t,e){var n;n=function(){return this}();try{n=n||new Function('return this')()}catch(t){'object'==typeof window&&(n=window)}t.exports=n},function(t,e){t.exports=require('jsdom-global')},function(t,e,n){const{OPERATIONS:r}=n(0),o=n(12),s=n(13),c=n(14);t.exports=function(t,e){switch(t){case r.CLEANUP:return new o(e);case r.VALIDATE:return new c(e);case r.PING:default:return new s(e)}}},function(t,e,n){const r=n(4),o=n(1),s=n(3),c=o.getInstance();t.exports=class extends s{constructor(t={}){const{form:e,data:n}=t;super({form:e,data:n}),this.data=n,this.form=e}execute(){const t={data:this.data};let e=r(this.form,t).data;try{e=JSON.stringify(e),c.send(e)}catch(t){c.sendError(t.toString())}finally{return Promise.resolve()}}}},function(t,e,n){const r=n(1),{PING_MESSAGE:o}=n(0),s=n(3),c=r.getInstance();t.exports=class extends s{execute(){return c.send(o),Promise.resolve()}}},function(t,e,n){const r=n(4),o=n(1),s=n(15),c=n(3),i=n(16),u=o.getInstance();t.exports=class extends c{constructor(t={}){const{form:e,data:n,resourcePath:r}=t;super({form:e,data:n}),this.data=n,this.form=e,this.resourcePath=r}execute(){const t={data:this.data},e=r(this.form,t);return i(this.resourcePath),s(this.form,e).then(t=>{try{t=JSON.stringify(t),u.send(t)}catch(t){u.sendError(t.toString())}}).catch(t=>{try{t=JSON.stringify(t),u.sendError(t)}catch(t){u.sendError(t.toString())}})}}},function(t,e,n){const{Formio:r}=n(5),o=document.body;t.exports=function(t,e){return new Promise((n,s)=>{r.createForm(o,t).then(t=>{t.once('error',t=>{t=t&&t.map(t=>t.message),s(t)}),t.once('submit',t=>{n(t)}),t.once('change',()=>{t.submit().then(()=>{}).catch(()=>{})}),t.submission=e}).catch(t=>{s(t)})})}},function(t,e,n){const r=n(17);let o='';t.exports=function(t){t!==o&&(r(t),o=t)}},function(t,e,n){const{Formio:r}=n(5),o=n(18),s=n(19),{CUSTOM_COMPONENTS_FOLDER_NAME:c}=n(0);function i(t={}){const{name:e,path:n}=t,o=require(n);r.registerComponent(e,o)}t.exports=function(t){if(!t)return;const e=o.existsSync(s.resolve(t,c))?s.resolve(t,c):void 0;e&&o.readdirSync(e).filter(t=>'.js'===s.extname(t)).map(t=>({name:t.slice(0,-s.extname(t).length),path:s.resolve(e,t)})).forEach(i)}},function(t,e){t.exports=require('fs')},function(t,e){t.exports=require('path')}]);