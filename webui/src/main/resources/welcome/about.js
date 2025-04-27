import {showModal} from './functions.js';

document.addEventListener('DOMContentLoaded', () => {
    const privacyLink = document.getElementById('privacy');
    const tosLink = document.getElementById('tos');
    if (privacyLink) {

        privacyLink.addEventListener('click', () => showModal('/privacy.html', false));
    }
    if (tosLink) {

        tosLink.addEventListener('click', () => showModal('/tos.html', false));
    }
});