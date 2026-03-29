(function () {
    const API = "";
    const storeKey = "flyte_token";
    const userKey = "flyte_user";

    let token = localStorage.getItem(storeKey);
    let currentUser = JSON.parse(localStorage.getItem(userKey) || "null");
    let allFlights = [];
    let stripePromise = null;
    let stripeClient = null;
    let stripeElements = null;
    let activeBooking = null;
    let activeTicket = null;

    const byId = (id) => document.getElementById(id);
    const hasHomePage = () => Boolean(byId("flightList"));

    function openModal(name) {
        const modal = byId(name + "Modal");
        if (modal) {
            modal.classList.add("active");
        }
    }

    function closeModal(name) {
        const modal = byId(name + "Modal");
        if (modal) {
            modal.classList.remove("active");
        }
    }

    function switchModal(current, next) {
        closeModal(current);
        window.setTimeout(() => openModal(next), 120);
    }

    function setMsg(id, text, type) {
        const el = byId(id);
        if (!el) {
            return;
        }
        el.textContent = text;
        el.className = "msg show " + type;
    }

    function clearMsg(id) {
        const el = byId(id);
        if (!el) {
            return;
        }
        el.textContent = "";
        el.className = "msg";
    }

    function safeError(payload, fallback) {
        if (typeof payload === "string" && payload.trim()) {
            return payload;
        }
        if (payload && typeof payload.message === "string" && payload.message.trim()) {
            return payload.message;
        }
        return fallback;
    }

    function seatText(value) {
        return value.replaceAll("_", " ").toLowerCase().replace(/\b\w/g, (char) => char.toUpperCase());
    }

    function seatMultiplier(value) {
        if (value === "PREMIUM_ECONOMY") {
            return 1.5;
        }
        if (value === "BUSINESS") {
            return 3;
        }
        if (value === "FIRST_CLASS") {
            return 5;
        }
        return 1;
    }

    function signedInMarkup() {
        const adminLink = currentUser && currentUser.role === "ROLE_ADMIN"
            ? '<a class="btn ghost" href="/login">Open Admin Portal</a>'
            : "";

        return '<span class="nav-user">Signed in as <strong>' + currentUser.username + "</strong></span>"
            + adminLink
            + '<button class="btn ghost" type="button" onclick="logout()">Sign out</button>';
    }

    function signedOutMarkup() {
        return '<button class="btn ghost" type="button" onclick="openModal(\'login\')">Passenger Sign In</button>'
            + '<a class="btn ghost" href="/login">Admin Portal</a>'
            + '<button class="btn primary" type="button" onclick="openModal(\'register\')">Create Account</button>';
    }

    function updateNav() {
        const nav = byId("navActions");
        if (!nav) {
            return;
        }
        nav.innerHTML = currentUser ? signedInMarkup() : signedOutMarkup();
    }

    function animateCounter(id, value) {
        const el = byId(id);
        if (!el) {
            return;
        }

        const target = Number(value) || 0;
        const startValue = Number(el.dataset.value || 0);
        const duration = 700;
        const startTime = performance.now();
        el.dataset.value = String(target);

        function frame(now) {
            const progress = Math.min((now - startTime) / duration, 1);
            const eased = 1 - Math.pow(1 - progress, 3);
            const current = Math.round(startValue + ((target - startValue) * eased));
            el.textContent = current.toLocaleString();

            if (progress < 1) {
                window.requestAnimationFrame(frame);
            }
        }

        window.requestAnimationFrame(frame);
    }

    function logout() {
        localStorage.removeItem(storeKey);
        localStorage.removeItem(userKey);
        token = null;
        currentUser = null;
        updateNav();

        const bookingList = byId("bookingList");
        if (bookingList) {
            bookingList.innerHTML = '<div class="empty">Sign in as a passenger to load your bookings.</div>';
        }
    }

    function renderStats(list) {
        animateCounter("cntFlights", list.length);
        animateCounter("heroFlights", list.length);
        animateCounter("cntRoutes", new Set(list.map((flight) => flight.origin + "|" + flight.destination)).size);
        animateCounter("cntSeats", list.reduce((sum, flight) => sum + (flight.availableSeats || 0), 0));
    }

    function renderFlights(list) {
        const resultsMeta = byId("resultsMeta");
        const flightList = byId("flightList");

        if (!resultsMeta || !flightList) {
            return;
        }

        resultsMeta.textContent = list.length
            ? list.length + " flight" + (list.length === 1 ? "" : "s") + " available for booking right now."
            : "No published flights matched your search.";

        if (!list.length) {
            flightList.innerHTML = '<div class="empty">No flights match the current filters.</div>';
            return;
        }

        flightList.innerHTML = list.map((flight) => (
            '<article class="card">'
            + '<div class="row">'
            + '<div><div class="route">' + flight.origin + ' -> ' + flight.destination + '</div>'
            + '<div class="meta-inline">Flight ' + flight.flightNumber + '</div></div>'
            + '<div class="price">KES ' + Number(flight.baseFare).toLocaleString() + '</div>'
            + '</div>'
            + '<div class="meta">'
            + '<span>Departing ' + new Date(flight.departureTime).toLocaleString("en-KE", { dateStyle: "medium", timeStyle: "short" }) + '</span>'
            + '<span>Arriving ' + new Date(flight.arrivalTime).toLocaleString("en-KE", { dateStyle: "medium", timeStyle: "short" }) + '</span>'
            + '<span>' + flight.availableSeats + " seat" + (flight.availableSeats === 1 ? "" : "s") + " remaining</span>"
            + '</div>'
            + '<div class="row">'
            + '<span class="hint">Economy from base fare. Premium classes calculated on booking.</span>'
            + '<button class="btn primary" type="button" onclick="openBooking(\'' + flight.flightNumber + '\')">Book flight</button>'
            + '</div>'
            + '</article>'
        )).join("");
    }

    function renderBookings(list) {
        const bookingList = byId("bookingList");
        if (!bookingList) {
            return;
        }

        if (!currentUser) {
            bookingList.innerHTML = '<div class="empty">Sign in as a passenger to load your bookings.</div>';
            return;
        }

        if (!list.length) {
            bookingList.innerHTML = '<div class="empty">No bookings yet. Choose a flight above and create your first booking.</div>';
            return;
        }

        bookingList.innerHTML = list.map((booking) => (
            '<article class="card">'
            + "<h3>" + booking.passengerName + "</h3>"
            + '<p class="booking-route">Flight ' + (booking.flight?.flightNumber || "Unknown")
            + " · " + (booking.flight?.origin || "?") + " -> " + (booking.flight?.destination || "?") + "</p>"
            + '<p class="booking-seat">' + seatText(booking.seatClass) + " · Seat " + booking.seatNumber + "</p>"
            + '<div class="row booking-row">'
            + '<span class="' + (booking.cancelled ? "status-cancelled" : "status-active") + '">'
            + (booking.cancelled ? "Cancelled" : "Active booking")
            + "</span>"
            + '<span class="price booking-price">KES ' + Number(booking.price).toLocaleString() + "</span>"
            + "</div>"
            + "</article>"
        )).join("");
    }

    function currentFlight(flightNumber) {
        return allFlights.find((flight) => flight.flightNumber === flightNumber) || null;
    }

    function paymentSummaryMarkup(booking) {
        const flight = booking.flight || currentFlight(booking.flightNumber) || {};
        return '<div class="summary-row"><span>Booking ID</span><strong>#' + booking.id + '</strong></div>'
            + '<div class="summary-row"><span>Passenger</span><strong>' + booking.passengerName + '</strong></div>'
            + '<div class="summary-row"><span>Route</span><strong>' + (flight.origin || "?") + " -> " + (flight.destination || "?") + '</strong></div>'
            + '<div class="summary-row"><span>Flight</span><strong>' + (flight.flightNumber || booking.flightNumber || "?") + '</strong></div>'
            + '<div class="summary-row"><span>Cabin</span><strong>' + seatText(booking.seatClass) + '</strong></div>'
            + '<div class="summary-row"><span>Seat</span><strong>' + booking.seatNumber + '</strong></div>'
            + '<div class="summary-row total"><span>Total due</span><strong>KES ' + Number(booking.price).toLocaleString() + '</strong></div>';
    }

    function ticketReference(booking, paymentReference) {
        const stamp = String(booking.id).padStart(4, "0");
        const suffix = (paymentReference || "PENDING").slice(-6).toUpperCase();
        return "FLYTE-" + stamp + "-" + suffix;
    }

    function ticketMarkup(ticket) {
        return '<div class="ticket-card">'
            + '<div class="ticket-head"><span class="ticket-eyebrow">Electronic ticket</span><strong>' + ticket.reference + '</strong></div>'
            + '<div class="ticket-route">' + ticket.origin + ' <span>→</span> ' + ticket.destination + '</div>'
            + '<div class="ticket-grid">'
            + '<div><span>Passenger</span><strong>' + ticket.passengerName + '</strong></div>'
            + '<div><span>Flight</span><strong>' + ticket.flightNumber + '</strong></div>'
            + '<div><span>Seat</span><strong>' + ticket.seatNumber + '</strong></div>'
            + '<div><span>Cabin</span><strong>' + seatText(ticket.seatClass) + '</strong></div>'
            + '<div><span>Departure</span><strong>' + ticket.departure + '</strong></div>'
            + '<div><span>Arrival</span><strong>' + ticket.arrival + '</strong></div>'
            + '<div><span>Amount paid</span><strong>KES ' + Number(ticket.price).toLocaleString() + '</strong></div>'
            + '<div><span>Payment reference</span><strong>' + ticket.paymentReference + '</strong></div>'
            + '</div>'
            + '</div>';
    }

    function buildTicket(booking, paymentReference) {
        const flight = booking.flight || currentFlight(booking.flightNumber) || {};
        return {
            reference: ticketReference(booking, paymentReference),
            passengerName: booking.passengerName,
            flightNumber: flight.flightNumber || booking.flightNumber || "Unknown",
            origin: flight.origin || "Unknown",
            destination: flight.destination || "Unknown",
            seatNumber: booking.seatNumber,
            seatClass: booking.seatClass,
            price: booking.price,
            departure: flight.departureTime
                ? new Date(flight.departureTime).toLocaleString("en-KE", { dateStyle: "medium", timeStyle: "short" })
                : "Pending schedule",
            arrival: flight.arrivalTime
                ? new Date(flight.arrivalTime).toLocaleString("en-KE", { dateStyle: "medium", timeStyle: "short" })
                : "Pending schedule",
            paymentReference: paymentReference || "Pending"
        };
    }

    async function ensureStripeLoaded() {
        if (window.Stripe) {
            return window.Stripe;
        }

        if (!stripePromise) {
            stripePromise = new Promise((resolve, reject) => {
                const script = document.createElement("script");
                script.src = "https://js.stripe.com/v3/";
                script.async = true;
                script.onload = () => resolve(window.Stripe);
                script.onerror = () => reject(new Error("Stripe.js could not be loaded."));
                document.head.appendChild(script);
            });
        }

        return stripePromise;
    }

    async function getStripeClient() {
        if (stripeClient) {
            return stripeClient;
        }

        const StripeCtor = await ensureStripeLoaded();
        const res = await fetch(API + "/api/payments/stripe/config", {
            headers: { Authorization: "Bearer " + token }
        });
        const payload = await res.json().catch(() => ({}));

        if (!res.ok || !payload.publishableKey) {
            throw new Error("Stripe is not configured yet. Add the publishable key and restart the app.");
        }

        stripeClient = StripeCtor(payload.publishableKey);
        return stripeClient;
    }

    async function openPaymentModal(booking) {
        activeBooking = booking;
        activeTicket = null;
        clearMsg("paymentMsg");
        const paymentSummary = byId("paymentSummary");
        const stripeElement = byId("stripeElement");
        const cardholderName = byId("cardholderName");
        const payNowBtn = byId("payNowBtn");

        if (paymentSummary) {
            paymentSummary.innerHTML = paymentSummaryMarkup(booking);
        }

        if (stripeElement) {
            stripeElement.innerHTML = "";
        }

        if (cardholderName) {
            cardholderName.value = booking.passengerName || (currentUser ? currentUser.username : "");
        }

        if (payNowBtn) {
            payNowBtn.disabled = true;
        }

        openModal("payment");

        try {
            const stripe = await getStripeClient();
            const intentRes = await fetch(API + "/api/payments/stripe/intent/" + booking.id, {
                method: "POST",
                headers: { Authorization: "Bearer " + token }
            });
            const intentPayload = await intentRes.json().catch(() => ({}));

            if (!intentRes.ok || !intentPayload.clientSecret) {
                throw new Error(safeError(intentPayload, "Could not initialize card payment."));
            }

            stripeElements = stripe.elements({ clientSecret: intentPayload.clientSecret });
            const paymentElement = stripeElements.create("payment", {
                layout: "tabs",
                defaultValues: {
                    billingDetails: {
                        name: booking.passengerName || ""
                    }
                }
            });
            paymentElement.mount("#stripeElement");

            if (payNowBtn) {
                payNowBtn.disabled = false;
            }
        } catch (error) {
            setMsg("paymentMsg", error.message || "Stripe could not be initialized.", "error");
        }
    }

    async function submitStripePayment() {
        clearMsg("paymentMsg");

        if (!activeBooking || !stripeClient || !stripeElements) {
            setMsg("paymentMsg", "Payment is not ready yet. Reopen the payment modal and try again.", "error");
            return;
        }

        const cardholderName = byId("cardholderName").value.trim();
        const payNowBtn = byId("payNowBtn");
        if (payNowBtn) {
            payNowBtn.disabled = true;
        }

        try {
            const result = await stripeClient.confirmPayment({
                elements: stripeElements,
                confirmParams: {
                    payment_method_data: {
                        billing_details: {
                            name: cardholderName || activeBooking.passengerName
                        }
                    }
                },
                redirect: "if_required"
            });

            if (result.error) {
                throw new Error(result.error.message || "Card payment failed.");
            }

            const syncRes = await fetch(API + "/api/payments/stripe/sync/" + activeBooking.id, {
                method: "POST",
                headers: { Authorization: "Bearer " + token }
            });
            const syncPayload = await syncRes.json().catch(() => ({}));

            if (!syncRes.ok || syncPayload.status !== "SUCCESS") {
                throw new Error(safeError(syncPayload, "Payment was processed but could not be confirmed in Flyte."));
            }

            setMsg("paymentMsg", "Payment successful. Your ticket is ready.", "success");
            activeTicket = buildTicket(activeBooking, syncPayload.transactionReference);
            await loadMyBookings();
            window.setTimeout(() => {
                closeModal("payment");
                showTicket();
            }, 500);
        } catch (error) {
            setMsg("paymentMsg", error.message || "Payment failed.", "error");
        } finally {
            if (payNowBtn) {
                payNowBtn.disabled = false;
            }
        }
    }

    function showTicket() {
        if (!activeTicket) {
            return;
        }

        clearMsg("ticketMsg");
        const ticketPreview = byId("ticketPreview");
        if (ticketPreview) {
            ticketPreview.innerHTML = ticketMarkup(activeTicket);
        }
        openModal("ticket");
    }

    function downloadTicket() {
        if (!activeTicket) {
            setMsg("ticketMsg", "No generated ticket is available yet.", "error");
            return;
        }

        const html = '<!DOCTYPE html><html><head><meta charset="UTF-8"><title>' + activeTicket.reference + '</title>'
            + '<style>body{font-family:Arial,sans-serif;background:#0b0b12;color:#f0ede8;padding:32px;}'
            + '.ticket{max-width:720px;margin:0 auto;border:1px solid #c9a84c;padding:24px;background:#13131f;}'
            + '.row{display:flex;justify-content:space-between;gap:16px;margin:10px 0;}'
            + 'h1,h2{margin:0 0 12px;color:#c9a84c;}strong{color:#fff;}small{color:#c5b792;letter-spacing:.18em;text-transform:uppercase;}</style></head><body>'
            + '<div class="ticket"><small>Flyte electronic ticket</small><h1>' + activeTicket.reference + '</h1>'
            + '<h2>' + activeTicket.origin + ' → ' + activeTicket.destination + '</h2>'
            + '<div class="row"><span>Passenger</span><strong>' + activeTicket.passengerName + '</strong></div>'
            + '<div class="row"><span>Flight</span><strong>' + activeTicket.flightNumber + '</strong></div>'
            + '<div class="row"><span>Departure</span><strong>' + activeTicket.departure + '</strong></div>'
            + '<div class="row"><span>Arrival</span><strong>' + activeTicket.arrival + '</strong></div>'
            + '<div class="row"><span>Seat</span><strong>' + activeTicket.seatNumber + '</strong></div>'
            + '<div class="row"><span>Cabin</span><strong>' + seatText(activeTicket.seatClass) + '</strong></div>'
            + '<div class="row"><span>Amount paid</span><strong>KES ' + Number(activeTicket.price).toLocaleString() + '</strong></div>'
            + '<div class="row"><span>Payment reference</span><strong>' + activeTicket.paymentReference + '</strong></div></div>'
            + '</body></html>';
        const blob = new Blob([html], { type: "text/html" });
        const url = URL.createObjectURL(blob);
        const link = document.createElement("a");
        link.href = url;
        link.download = activeTicket.reference + ".html";
        link.click();
        URL.revokeObjectURL(url);
    }

    function printTicket() {
        if (!activeTicket) {
            setMsg("ticketMsg", "No generated ticket is available yet.", "error");
            return;
        }

        const win = window.open("", "_blank", "width=900,height=700");
        if (!win) {
            setMsg("ticketMsg", "Allow popups to print the ticket.", "error");
            return;
        }

        win.document.write('<!DOCTYPE html><html><head><title>' + activeTicket.reference + '</title></head><body>'
            + ticketMarkup(activeTicket) + '</body></html>');
        win.document.close();
        win.focus();
        win.print();
    }

    async function loadFlights() {
        const flightList = byId("flightList");
        if (!flightList) {
            return;
        }

        flightList.innerHTML = '<div class="empty">Loading flights...</div>';

        try {
            const res = await fetch(API + "/api/flights");
            const payload = await res.json();
            allFlights = Array.isArray(payload) ? payload : [];
            renderStats(allFlights);
            renderFlights(allFlights);
        } catch {
            flightList.innerHTML = '<div class="empty">Could not load flights. Make sure the Flyte backend is running.</div>';
        }
    }

    async function searchFlights() {
        const origin = byId("sOrigin").value.trim();
        const destination = byId("sDest").value.trim();
        const date = byId("sDate").value;
        const seat = byId("sSeat").value;
        const feedback = byId("searchFeedback");
        let results = allFlights;

        if (origin && destination && date) {
            const params = new URLSearchParams({
                origin,
                destination,
                from: date + "T00:00:00",
                to: date + "T23:59:59"
            });

            try {
                const res = await fetch(API + "/api/flights/search?" + params);
                const payload = await res.json();
                results = Array.isArray(payload) ? payload : [];
            } catch {
                feedback.textContent = "Search failed because the backend could not be reached.";
                return;
            }
        } else {
            results = allFlights.filter((flight) => (
                (!origin || flight.origin.toLowerCase().includes(origin.toLowerCase()))
                && (!destination || flight.destination.toLowerCase().includes(destination.toLowerCase()))
                && (!date || flight.departureTime.startsWith(date))
            ));
        }

        if (seat) {
            results = results.map((flight) => ({
                ...flight,
                baseFare: Number(flight.baseFare) * seatMultiplier(seat)
            }));
        }

        renderFlights(results);
        feedback.textContent = results.length
            ? "Showing " + results.length + " result" + (results.length === 1 ? "" : "s")
                + (seat ? " in " + seatText(seat) : "") + "."
            : "No flights matched your criteria.";

        byId("flights").scrollIntoView({ behavior: "smooth" });
    }

    function resetSearch() {
        ["sOrigin", "sDest", "sDate", "sSeat"].forEach((id) => {
            byId(id).value = "";
        });
        byId("searchFeedback").textContent = "Showing all published flights.";
        renderFlights(allFlights);
    }

    function setTripTab(mode) {
        const states = {
            "one-way": "tripOneWay",
            "round-trip": "tripRoundTrip",
            "multi-city": "tripMultiCity"
        };

        Object.values(states).forEach((id) => {
            const button = byId(id);
            if (button) {
                button.classList.remove("active");
            }
        });

        const active = byId(states[mode] || states["one-way"]);
        if (active) {
            active.classList.add("active");
        }
    }

    function openBooking(flightNumber) {
        if (!token) {
            openModal("login");
            setMsg("loginMsg", "Sign in as a passenger before booking a flight.", "info");
            return;
        }

        clearMsg("bookingMsg");
        byId("bookFlight").value = flightNumber;
        byId("bookName").value = currentUser ? currentUser.username : "";
        byId("bookSeat").value = "";
        byId("bookClass").value = "ECONOMY";
        openModal("booking");
    }

    async function doLogin() {
        clearMsg("loginMsg");
        const username = byId("loginUsername").value.trim();
        const password = byId("loginPassword").value;

        if (!username || !password) {
            setMsg("loginMsg", "Enter both username and password.", "error");
            return;
        }

        try {
            const res = await fetch(API + "/api/auth/login", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ username, password })
            });
            const payload = await res.json().catch(() => ({}));

            if (!res.ok) {
                setMsg("loginMsg", safeError(payload, "Login failed."), "error");
                return;
            }

            token = payload.token;
            currentUser = { username: payload.username, role: payload.role };
            localStorage.setItem(storeKey, token);
            localStorage.setItem(userKey, JSON.stringify(currentUser));
            setMsg("loginMsg", "Welcome back, " + payload.username + ".", "success");
            updateNav();

            window.setTimeout(async () => {
                closeModal("login");
                await loadMyBookings();
            }, 500);
        } catch {
            setMsg("loginMsg", "Cannot reach the backend. Start the server and try again.", "error");
        }
    }

    async function doRegister() {
        clearMsg("registerMsg");
        const username = byId("regUsername").value.trim();
        const email = byId("regEmail").value.trim();
        const password = byId("regPassword").value;

        if (!username || !email || !password) {
            setMsg("registerMsg", "Complete all registration fields.", "error");
            return;
        }

        if (password.length < 8) {
            setMsg("registerMsg", "Password must be at least 8 characters.", "error");
            return;
        }

        try {
            const res = await fetch(API + "/api/auth/register", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ username, email, password, role: "PASSENGER" })
            });
            const text = await res.text();

            if (!res.ok) {
                setMsg("registerMsg", safeError(text, "Registration failed."), "error");
                return;
            }

            setMsg("registerMsg", "Account created. Signing you in now.", "success");
            byId("loginUsername").value = username;
            byId("loginPassword").value = password;

            window.setTimeout(async () => {
                switchModal("register", "login");
                await doLogin();
            }, 650);
        } catch {
            setMsg("registerMsg", "Cannot reach the backend. Start the server and try again.", "error");
        }
    }

    async function submitBooking() {
        clearMsg("bookingMsg");
        const flightNumber = byId("bookFlight").value;
        const passengerName = byId("bookName").value.trim();
        const seatNumber = byId("bookSeat").value.trim();
        const seatClass = byId("bookClass").value;

        if (!token) {
            setMsg("bookingMsg", "Your session is missing. Sign in again.", "error");
            return;
        }

        if (!passengerName || !seatNumber) {
            setMsg("bookingMsg", "Passenger name and seat number are required.", "error");
            return;
        }

        try {
            const res = await fetch(API + "/api/bookings", {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                    Authorization: "Bearer " + token
                },
                body: JSON.stringify({ flightNumber, passengerName, seatClass, seatNumber })
            });
            const payload = await res.json().catch(() => ({}));

            if (!res.ok) {
                setMsg("bookingMsg", safeError(payload, "Booking failed."), "error");
                return;
            }

            setMsg("bookingMsg", "Booking confirmed. Proceed to payment to issue your ticket.", "success");
            await loadFlights();
            await loadMyBookings();
            window.setTimeout(async () => {
                closeModal("booking");
                await openPaymentModal(payload);
            }, 550);
        } catch {
            setMsg("bookingMsg", "Could not submit the booking request.", "error");
        }
    }

    async function loadMyBookings() {
        if (!token || !currentUser) {
            renderBookings([]);
            return;
        }

        try {
            const res = await fetch(API + "/api/bookings/my", {
                headers: { Authorization: "Bearer " + token }
            });

            if (!res.ok) {
                renderBookings([]);
                return;
            }

            const payload = await res.json();
            renderBookings(Array.isArray(payload) ? payload : []);
        } catch {
            const bookingList = byId("bookingList");
            if (bookingList) {
                bookingList.innerHTML = '<div class="empty">Could not load bookings for the current user.</div>';
            }
        }
    }

    function initModalDismiss() {
        document.querySelectorAll(".overlay").forEach((overlay) => {
            overlay.addEventListener("click", (event) => {
                if (event.target === overlay) {
                    overlay.classList.remove("active");
                }
            });
        });
    }

    function initHomePage() {
        if (!hasHomePage()) {
            return;
        }

        updateNav();
        loadFlights();
        loadMyBookings();
    }

    window.openModal = openModal;
    window.closeModal = closeModal;
    window.switchModal = switchModal;
    window.searchFlights = searchFlights;
    window.resetSearch = resetSearch;
    window.setTripTab = setTripTab;
    window.openBooking = openBooking;
    window.doLogin = doLogin;
    window.doRegister = doRegister;
    window.submitBooking = submitBooking;
    window.submitStripePayment = submitStripePayment;
    window.downloadTicket = downloadTicket;
    window.printTicket = printTicket;
    window.logout = logout;

    document.addEventListener("DOMContentLoaded", function () {
        initModalDismiss();
        initHomePage();
    });
}());
