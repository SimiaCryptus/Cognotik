import {showModal} from "./functions.js";

document.addEventListener('DOMContentLoaded', function () {
    fetchUserInfo();
    fetchAppList();

    document.querySelector(".close").onclick = function () {
        document.getElementById("modal").style.display = "none";
    }
});

function fetchUserInfo() {
    fetch('/api/user')
        .then(response => response.json())
        .then(user => {
            if (user) {
                document.getElementById('login').style.display = 'none';
                const usernameElem = document.getElementById('username');
                usernameElem.textContent = user.username;
                usernameElem.style.visibility = 'visible';
                document.getElementById('user-settings').style.visibility = 'visible';
                document.getElementById('user-usage').style.visibility = 'visible';
                document.getElementById('logout').style.visibility = 'visible';
            }
        })
        .catch(error => console.error('Error fetching user info:', error));
}

function fetchAppList() {
    fetch('/api/apps')
        .then(response => response.json())
        .then(apps => {
            const appList = document.getElementById('application-list');
            apps.forEach(app => {
                const row = document.createElement('tr');

                const nameCell = document.createElement('td');
                if (app.thumbnail) {
                    const img = document.createElement('img');
                    img.src = app.thumbnail;
                    img.alt = app.applicationName;
                    img.className = 'app-thumbnail';
                    img.onclick = () => showImageModal(app.thumbnail);
                    nameCell.appendChild(img);
                }
                const nameText = document.createTextNode(app.applicationName);
                nameCell.appendChild(nameText);
                row.appendChild(nameCell);

                const sessionsCell = document.createElement('td');
                const sessionsLink = document.createElement('a');
                sessionsLink.href = 'javascript:void(0);';
                sessionsLink.textContent = 'List Sessions';
                sessionsLink.onclick = () => showModal(`${app.path}/sessions`);
                sessionsCell.appendChild(sessionsLink);
                row.appendChild(sessionsCell);

                if (app.canWrite) {
                    const privateCell = document.createElement('td');
                    const privateLink = document.createElement('a');
                    privateLink.className = 'new-session-link';
                    privateLink.href = `${app.path}/#${generateUserSessionId()}`;
                    privateLink.textContent = 'New Session';
                    privateCell.appendChild(privateLink);
                    row.appendChild(privateCell);
                } else {
                    row.appendChild(document.createElement('td'));
                }

                appList.appendChild(row);
            });
        })
        .catch(error => console.error('Error fetching app list:', error));
}

function generateUserSessionId() {
    const date = new Date();
    const yyyyMMdd = date.toISOString().slice(0, 10).replace(/-/g, "");
    return `U-${yyyyMMdd}-${generateRandomId()}`;
}

function generateRandomId() {
    return Math.random().toString(36).substr(2, 4);
}

function showImageModal(src) {
    const modal = document.getElementById("modal");
    const modalContent = document.getElementById("modal-content");
    modalContent.innerHTML = '<img src="' + src + '" style="width: 100%;">';
    modal.style.display = "block";
}