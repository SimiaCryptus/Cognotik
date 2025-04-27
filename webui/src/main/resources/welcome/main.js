import {closeModal, showModal} from "./functions.js";

let loadImages = "true";

document.addEventListener('DOMContentLoaded', () => {
    if (typeof mermaid !== 'undefined') mermaid.run();

    document.querySelectorAll('.tabs-container').forEach(tabsContainer => {
        const savedTab = localStorage.getItem(`selectedTab_${tabsContainer.id}`);
        if (savedTab) {
            const savedButton = tabsContainer.querySelector(`.tab-button[data-for-tab="${savedTab}"]`);
            if (savedButton) {
                savedButton.classList.add('active');
                const forTab = savedButton.getAttribute('data-for-tab');
                const selectedContent = tabsContainer.querySelector(`.tab-content[data-tab="${forTab}"]`);
                if (selectedContent) {
                    selectedContent.classList.add('active');
                    selectedContent.style.display = 'block';
                }
                console.log(`Restored saved tab: ${savedTab}`);
            }
        }
    });
    document.querySelectorAll('.tabs-container').forEach(tabsContainer => {
        const savedTab = localStorage.getItem(`selectedTab_${tabsContainer.id}`);
        if (savedTab) {
            const savedButton = tabsContainer.querySelector(`.tab-button[data-for-tab="${savedTab}"]`);
            if (savedButton) {
                savedButton.click();
                console.log(`Restored saved tab: ${savedTab}`);
            }
        }
    });

    const historyElement = document.getElementById('history');
    if (historyElement) historyElement.addEventListener('click', () => showModal('sessions'));
    const settingsElement = document.getElementById('settings');
    if (settingsElement) settingsElement.addEventListener('click', () => showModal('settings'));
    const usageElement = document.getElementById('usage');
    if (usageElement) usageElement.addEventListener('click', () => showModal('usage'));
    const verboseElement = document.getElementById('verbose');
    if (verboseElement) verboseElement.addEventListener('click', () => toggleVerbose());
    const deleteElement = document.getElementById('delete');
    if (deleteElement) deleteElement.addEventListener('click', () => showModal('delete'));
    const cancelElement = document.getElementById('cancel');
    if (cancelElement) cancelElement.addEventListener('click', () => showModal('cancel'));
    const threadsElement = document.getElementById('threads');
    if (threadsElement) threadsElement.addEventListener('click', () => showModal('threads'));
    const shareElement = document.getElementById('share');
    if (shareElement) {
        shareElement.addEventListener('click', () => showModal('share?url=' + encodeURIComponent(window.location.href) + "&loadImages=" + loadImages, false));
    }
    const closeElement = document.querySelector('.close');
    if (closeElement) closeElement.addEventListener('click', closeModal);

    const loginLink = document.getElementById('login');
    const usernameLink = document.getElementById('username');
    const userSettingsLink = document.getElementById('user-settings');
    const userUsageLink = document.getElementById('user-usage');
    const logoutLink = document.getElementById('logout');

    window.addEventListener('click', (event) => {
        if (event.target === document.getElementById('modal')) {
            closeModal();
        }
    });

    fetch('/userInfo')
        .then(response => {
            if (!response.ok) {
                throw new Error('Network response was not ok');
            }
            return response.json();
        })
        .then(data => {
            if (data.name) {

                usernameLink.textContent = data.name;
                usernameLink.style = 'visibility: visible';

                userSettingsLink.addEventListener('click', () => showModal('/userSettings'));
                userSettingsLink.style = 'visibility: visible';

                userUsageLink.addEventListener('click', () => showModal('/usage', false));
                userUsageLink.style = 'visibility: visible';

                logoutLink.href = '/logout';
                logoutLink.style = 'visibility: visible';

                loginLink.style = 'visibility: hidden';
            }
        })
        .catch(error => {
            console.error('There was a problem with the fetch operation:', error);
        });

    updateTabs();
});