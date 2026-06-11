document.addEventListener("DOMContentLoaded", function () {

    if (!window.revenueHistory) {
        console.log("No revenue history");
        return;
    }

    try {

        const revenueData =
            JSON.parse(window.revenueHistory);

        console.log(revenueData);

        if (!revenueData.length) {
            return;
        }
        revenueData.reverse();

        const labels =
            revenueData.map(x =>
                x.periodName || x.periodId
            );

        const revenues =
            revenueData.map(x =>
                x.revenue || 0
            );

        const ctx =
            document.getElementById("revenueChart");

        if (!ctx) {
            return;
        }

        new Chart(ctx, {
            type: "line",
            data: {
                labels: labels,
                datasets: [{
                    label: "Revenue",
                    data: revenues,
                    tension: 0.3
                }]
            }
        });

    } catch (e) {

        console.error(
            "Revenue chart error",
            e
        );

    }

});