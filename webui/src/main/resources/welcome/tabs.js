function updateTabs() {
    document.querySelectorAll('.tab-button').forEach(button => {

        button.addEventListener('click', (event) => {

            event.stopPropagation();
            const forTab = button.getAttribute('data-for-tab');
            const tabsContainerId = button.closest('.tabs-container').id;


            localStorage.setItem(`selectedTab_${tabsContainerId}`, forTab);
            let tabsParent = button.closest('.tabs-container');
            tabsParent.querySelectorAll('.tab-button').forEach(tabButton => {
                if (tabButton.closest('.tabs-container') === tabsParent) tabButton.classList.remove('active');
            });
            button.classList.add('active');

            let selectedContent = null;
            tabsParent.querySelectorAll('.tab-content').forEach(content => {
                if (content.closest('.tabs-container') === tabsParent) {
                    if (content.getAttribute('data-tab') === forTab) {
                        content.classList.add('active');
                        content.style.display = 'block';


                        selectedContent = content;
                    } else {
                        content.classList.remove('active');
                        content.style.display = 'none';


                    }
                }
            });
            if (selectedContent !== null) updateNestedTabs(selectedContent);
        });

        const savedTab = localStorage.getItem(`selectedTab_${button.closest('.tabs-container').id}`);
        if (button.getAttribute('data-for-tab') === savedTab) {
            button.dispatchEvent(new Event('click'));
        }
    });
}

function updateNestedTabs(element) {
    element.querySelectorAll('.tabs-container').forEach(tabsContainer => {
        try {

            let hasActiveButton = false;
            tabsContainer.querySelectorAll('.tab-button').forEach(nestedButton => {
                if (nestedButton.classList.contains('active')) {
                    hasActiveButton = true;

                }
            });
            if (!hasActiveButton) {
                /* Determine if a tab-content element in this tabs-container has the active class. If so, use its data-tab value to find the matching button and ensure it is marked active */
                const activeContent = tabsContainer.querySelector('.tab-content.active');
                if (activeContent) {
                    const activeTab = activeContent.getAttribute('data-tab');
                    const activeButton = tabsContainer.querySelector(`.tab-button[data-for-tab="${activeTab}"]`);
                    if (activeButton !== null) {
                        activeButton.classList.add('active');

                    }
                } else {
                    /* Add 'active' to the class list of the first button */
                    const firstButton = tabsContainer.querySelector('.tab-button');
                    if (firstButton !== null) {
                        firstButton.classList.add('active');

                    }
                }
            }
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

                }
            }
        } catch (e) {

        }
    });
}

document.addEventListener('DOMContentLoaded', () => {

    updateTabs();
    updateNestedTabs(document);

});

window.updateTabs = updateTabs; // Expose updateTabs to the global scope