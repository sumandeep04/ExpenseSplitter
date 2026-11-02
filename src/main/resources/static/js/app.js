document.addEventListener("DOMContentLoaded", () => {
    const method = document.getElementById("splitMethod");
    const form = document.getElementById("expenseForm");
    if (!method || !form) return;

    const updateSplitFields = () => {
        const value = method.value;
        document.querySelectorAll(".split-input").forEach((field) => {
            field.hidden = value === "EQUAL" || (field.classList.contains("percent-input") !== (value === "PERCENTAGE"));
        });
        document.querySelectorAll(".participant-row").forEach((row) => {
            const checkbox = row.querySelector('input[name="participantIds"]');
            row.classList.toggle("participant-disabled", !checkbox.checked);
            row.querySelectorAll(".split-input input").forEach((input) => {
                input.disabled = !checkbox.checked || value === "EQUAL";
                input.required = checkbox.checked && value !== "EQUAL";
            });
        });
    };

    method.addEventListener("change", updateSplitFields);
    document.querySelectorAll('input[name="participantIds"]').forEach((checkbox) => checkbox.addEventListener("change", updateSplitFields));
    updateSplitFields();
});
