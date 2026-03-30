(function () {
    const API = "";
    const storeKey = "flyte_token";
    const userKey = "flyte_user";
    const themeKey = "flyte_theme";

    let token = localStorage.getItem(storeKey);
    let currentUser = JSON.parse(localStorage.getItem(userKey) || "null");
    let allFlights = [];
    let stripePromise = null;
    let stripeClient = null;
    let stripeElements = null;
    let stripeCardElement = null;
    let stripeClientSecret = null;
    let stripeReadyForBookingId = null;
    let activeBooking = null;
    let activeTicket = null;
    let activePaymentMethod = "MPESA";

    const byId = (id) => document.getElementById(id);
    const hasHomePage = () => Boolean(byId("flightList"));

    function csrfHeaderName() {
        const meta = document.querySelector('meta[name="_csrf_header"]');
        return meta ? meta.getAttribute("content") : "X-XSRF-TOKEN";
    }

    function csrfToken() {
        const meta = document.querySelector('meta[name="_csrf"]');
        if (meta && meta.getAttribute("content")) {
            return meta.getAttribute("content");
        }

        const match = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]+)/);
        return match ? decodeURIComponent(match[1]) : "";
    }

    function withCsrf(headers) {
        const tokenValue = csrfToken();
        if (!tokenValue) {
            return headers || {};
        }

        return {
            ...(headers || {}),
            [csrfHeaderName()]: tokenValue
        };
    }

    function refreshThemeToggleLabels(theme) {
        document.querySelectorAll("[data-theme-toggle-label]").forEach((node) => {
            node.textContent = theme === "dark" ? "Light Mode" : "Dark Mode";
        });
    }

    function applyTheme(theme) {
        const resolved = theme === "light" ? "light" : "dark";
        document.documentElement.setAttribute("data-theme", resolved);
        refreshThemeToggleLabels(resolved);
    }

    function initTheme() {
        let theme = "dark";
        try {
            const saved = localStorage.getItem(themeKey);
            if (saved === "light" || saved === "dark") {
                theme = saved;
            } else if (window.matchMedia && !window.matchMedia("(prefers-color-scheme: dark)").matches) {
                theme = "light";
            }
        } catch (error) {
            theme = "dark";
        }
        applyTheme(theme);
    }

    function toggleTheme() {
        const current = document.documentElement.getAttribute("data-theme") === "light" ? "light" : "dark";
        const next = current === "dark" ? "light" : "dark";
        applyTheme(next);
        try {
            localStorage.setItem(themeKey, next);
        } catch (error) {
            // Ignore storage errors; theme still applies for the current session.
        }
    }

    function initScrollProgress() {
        const bar = byId("scrollProgressBar");
        if (!bar) {
            return;
        }

        let ticking = false;
        const update = function () {
            const doc = document.documentElement;
            const maxScroll = doc.scrollHeight - doc.clientHeight;
            const progress = maxScroll > 0 ? Math.min(Math.max(window.scrollY / maxScroll, 0), 1) : 0;
            bar.style.transform = "scaleX(" + progress + ")";
            ticking = false;
        };

        const requestUpdate = function () {
            if (!ticking) {
                ticking = true;
                window.requestAnimationFrame(update);
            }
        };

        window.addEventListener("scroll", requestUpdate, { passive: true });
        window.addEventListener("resize", requestUpdate);
        requestUpdate();
    }

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
            + '<button class="btn ghost" type="button" data-action="logout">Sign out</button>';
    }

    function signedOutMarkup() {
        return '<button class="btn ghost" type="button" data-action="open-modal" data-modal="login">Passenger Sign In</button>'
            + '<a class="btn ghost" href="/login">Admin Portal</a>'
            + '<button class="btn primary" type="button" data-action="open-modal" data-modal="register">Create Account</button>';
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
            + '<button class="btn primary" type="button" data-action="open-booking" data-flight-number="' + flight.flightNumber + '">Book flight</button>'
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
            + '<div class="actions nav-actions">'
            + (booking.cancelled
                ? '<button class="btn ghost" type="button" disabled>Unavailable</button>'
                : '<button class="btn primary" type="button" data-action="pay-for-booking" data-booking-id="' + booking.id + '">Pay now</button>'
                    + '<button class="btn ghost btn-danger" type="button" data-action="cancel-booking" data-booking-id="' + booking.id + '">Cancel</button>')
            + "</div>"
            + "</article>"
        )).join("");
    }

    async function cancelBooking(bookingId) {
        clearMsg("bookingActionMsg");
        clearMsg("bookingMsg");

        if (!token || !currentUser) {
            openModal("login");
            setMsg("loginMsg", "Sign in as a passenger to manage your bookings.", "info");
            return;
        }

        if (!window.confirm("Cancel this booking?")) {
            return;
        }

        try {
            const res = await fetch(API + "/api/bookings/" + bookingId, {
                method: "DELETE",
                headers: withCsrf({ Authorization: "Bearer " + token })
            });
            const payload = await res.json().catch(() => ({}));

            if (!res.ok) {
                throw new Error(safeError(payload, "Could not cancel this booking."));
            }

            setMsg("bookingActionMsg", "Booking cancelled successfully.", "success");
            await loadFlights();
            await loadMyBookings();
        } catch (error) {
            setMsg("bookingActionMsg", error.message || "Could not cancel this booking.", "error");
        }
    }

    async function payForBooking(bookingId) {
        clearMsg("paymentMsg");
        clearMsg("mpesaMsg");
        if (!token || !currentUser) {
            openModal("login");
            setMsg("loginMsg", "Sign in as a passenger to continue payment.", "info");
            return;
        }

        try {
            const res = await fetch(API + "/api/bookings/my", {
                headers: { Authorization: "Bearer " + token }
            });
            const payload = await res.json().catch(() => ([]));
            if (!res.ok || !Array.isArray(payload)) {
                throw new Error("Could not load your bookings for payment.");
            }

            const booking = payload.find((item) => item.id === bookingId);
            if (!booking) {
                throw new Error("Booking not found. Refresh and try again.");
            }
            if (booking.cancelled) {
                throw new Error("Cancelled bookings cannot be paid.");
            }

            await openPaymentModal(booking);
        } catch (error) {
            setMsg("bookingMsg", error.message || "Could not open payment for this booking.", "error");
        }
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
        activePaymentMethod = "MPESA";
        stripeReadyForBookingId = null;
        stripeElements = null;
        stripeCardElement = null;
        stripeClientSecret = null;
        clearMsg("paymentMsg");
        clearMsg("mpesaMsg");
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
        const mpesaPhone = byId("mpesaPhone");
        if (mpesaPhone) {
            mpesaPhone.value = "";
        }

        openModal("payment");
        switchPaymentMethod("MPESA");
    }

    async function initializeStripeForBooking() {
        if (!activeBooking) {
            return;
        }
        if (stripeReadyForBookingId === activeBooking.id && stripeElements) {
            return;
        }

        clearMsg("paymentMsg");
        const payNowBtn = byId("payNowBtn");
        const stripeElement = byId("stripeElement");
        if (payNowBtn) {
            payNowBtn.disabled = true;
        }
        if (stripeElement) {
            stripeElement.innerHTML = "";
        }
        stripeCardElement = null;
        stripeClientSecret = null;

        try {
            const stripe = await getStripeClient();
            const intentRes = await fetch(API + "/api/payments/stripe/intent/" + activeBooking.id, {
                method: "POST",
                headers: withCsrf({ Authorization: "Bearer " + token })
            });
            const intentPayload = await intentRes.json().catch(() => ({}));

            if (!intentRes.ok || !intentPayload.clientSecret) {
                throw new Error(safeError(intentPayload, "Could not initialize card payment."));
            }

            stripeClientSecret = intentPayload.clientSecret;
            stripeElements = stripe.elements();
            stripeCardElement = stripeElements.create("card");
            stripeCardElement.mount("#stripeElement");
            stripeReadyForBookingId = activeBooking.id;

            if (payNowBtn) {
                payNowBtn.disabled = false;
            }
        } catch (error) {
            setMsg("paymentMsg", error.message || "Stripe could not be initialized.", "error");
        }
    }

    function normalizePhone(value) {
        const digits = (value || "").replace(/\D/g, "");
        if (digits.startsWith("254") && digits.length === 12) {
            return digits;
        }
        if (digits.startsWith("0") && digits.length === 10) {
            return "254" + digits.slice(1);
        }
        if (digits.startsWith("7") && digits.length === 9) {
            return "254" + digits;
        }
        return null;
    }

    async function submitMpesaPayment() {
        clearMsg("paymentMsg");

        if (!activeBooking) {
            setMsg("paymentMsg", "No active booking selected for payment.", "error");
            return;
        }

        const mpesaPhoneInput = byId("mpesaPhone");
        const phoneNumber = normalizePhone(mpesaPhoneInput ? mpesaPhoneInput.value.trim() : "");
        const mpesaPayBtn = byId("mpesaPayBtn");

        if (!phoneNumber) {
            setMsg("mpesaMsg", "Enter a valid Safaricom number (e.g. 0712345678).", "error");
            return;
        }

        if (mpesaPayBtn) {
            mpesaPayBtn.disabled = true;
        }
        setMsg("mpesaMsg", "Sending STK push request...", "info");

        try {
            const res = await fetch(API + "/api/payments/mpesa/pay", {
                method: "POST",
                headers: withCsrf({
                    "Content-Type": "application/json",
                    Authorization: "Bearer " + token
                }),
                body: JSON.stringify({ bookingId: activeBooking.id, phoneNumber })
            });
            const payload = await res.json().catch(() => ({}));

            if (!res.ok) {
                throw new Error(safeError(payload, "Could not start Mpesa payment."));
            }

            setMsg("mpesaMsg", "Mpesa prompt sent. Check your phone and enter your PIN to complete payment.", "success");
        } catch (error) {
            setMsg("mpesaMsg", error.message || "Mpesa payment failed.", "error");
        } finally {
            if (mpesaPayBtn) {
                mpesaPayBtn.disabled = false;
            }
        }
    }

    function switchPaymentMethod(method) {
        activePaymentMethod = method === "STRIPE" ? "STRIPE" : "MPESA";

        const mpesaTab = byId("mpesaTab");
        const stripeTab = byId("stripeTab");
        const mpesaPanel = byId("mpesaPanel");
        const stripePanel = byId("stripePanel");

        if (mpesaTab) {
            mpesaTab.classList.toggle("active", activePaymentMethod === "MPESA");
        }
        if (stripeTab) {
            stripeTab.classList.toggle("active", activePaymentMethod === "STRIPE");
        }
        if (mpesaPanel) {
            mpesaPanel.style.display = activePaymentMethod === "MPESA" ? "block" : "none";
        }
        if (stripePanel) {
            stripePanel.style.display = activePaymentMethod === "STRIPE" ? "block" : "none";
        }

        if (activePaymentMethod === "STRIPE") {
            clearMsg("mpesaMsg");
            initializeStripeForBooking();
        } else {
            clearMsg("paymentMsg");
            clearMsg("mpesaMsg");
        }
    }

    async function submitStripePayment() {
        clearMsg("paymentMsg");

        if (!activeBooking || !stripeClient || !stripeCardElement || !stripeClientSecret) {
            setMsg("paymentMsg", "Payment is not ready yet. Reopen the payment modal and try again.", "error");
            return;
        }

        const cardholderName = byId("cardholderName").value.trim();
        const payNowBtn = byId("payNowBtn");
        if (payNowBtn) {
            payNowBtn.disabled = true;
        }

        try {
            const result = await stripeClient.confirmCardPayment(stripeClientSecret, {
                payment_method: {
                    card: stripeCardElement,
                    billing_details: {
                        name: cardholderName || activeBooking.passengerName
                    }
                }
            });

            if (result.error) {
                throw new Error(result.error.message || "Card payment failed.");
            }

            const syncRes = await fetch(API + "/api/payments/stripe/sync/" + activeBooking.id, {
                method: "POST",
                headers: withCsrf({ Authorization: "Bearer " + token })
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
                headers: withCsrf({ "Content-Type": "application/json" }),
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
                headers: withCsrf({ "Content-Type": "application/json" }),
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
                headers: withCsrf({
                    "Content-Type": "application/json",
                    Authorization: "Bearer " + token
                }),
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

    function scrollToTarget(targetId) {
        const target = byId(targetId);
        if (target) {
            target.scrollIntoView({ behavior: "smooth" });
        }
    }

    function initActionHandlers() {
        document.addEventListener("click", (event) => {
            const trigger = event.target.closest("[data-action]");
            if (!trigger) {
                return;
            }

            const action = trigger.dataset.action;

            if (action === "toggle-theme") {
                event.preventDefault();
                toggleTheme();
                return;
            }

            if (action === "open-modal") {
                event.preventDefault();
                openModal(trigger.dataset.modal);
                return;
            }

            if (action === "close-modal") {
                event.preventDefault();
                closeModal(trigger.dataset.modal);
                return;
            }

            if (action === "switch-modal") {
                event.preventDefault();
                switchModal(trigger.dataset.currentModal, trigger.dataset.nextModal);
                return;
            }

            if (action === "scroll-to") {
                event.preventDefault();
                scrollToTarget(trigger.dataset.target);
                return;
            }

            if (action === "set-trip-tab") {
                event.preventDefault();
                setTripTab(trigger.dataset.tripMode);
                return;
            }

            if (action === "switch-payment-method") {
                event.preventDefault();
                switchPaymentMethod(trigger.dataset.method);
                return;
            }

            if (action === "download-ticket") {
                event.preventDefault();
                downloadTicket();
                return;
            }

            if (action === "print-ticket") {
                event.preventDefault();
                printTicket();
                return;
            }

            if (action === "logout") {
                event.preventDefault();
                logout();
                return;
            }

            if (action === "open-booking") {
                event.preventDefault();
                openBooking(trigger.dataset.flightNumber);
                return;
            }

            if (action === "pay-for-booking") {
                event.preventDefault();
                payForBooking(Number(trigger.dataset.bookingId));
                return;
            }

            if (action === "cancel-booking") {
                event.preventDefault();
                cancelBooking(Number(trigger.dataset.bookingId));
            }
        });

        const searchForm = byId("searchForm");
        if (searchForm) {
            searchForm.addEventListener("submit", (event) => {
                event.preventDefault();
                searchFlights();
            });
        }

        const loginForm = byId("loginForm");
        if (loginForm) {
            loginForm.addEventListener("submit", (event) => {
                event.preventDefault();
                doLogin();
            });
        }

        const registerForm = byId("registerForm");
        if (registerForm) {
            registerForm.addEventListener("submit", (event) => {
                event.preventDefault();
                doRegister();
            });
        }

        const bookingForm = byId("bookingForm");
        if (bookingForm) {
            bookingForm.addEventListener("submit", (event) => {
                event.preventDefault();
                submitBooking();
            });
        }

        const mpesaForm = byId("mpesaForm");
        if (mpesaForm) {
            mpesaForm.addEventListener("submit", (event) => {
                event.preventDefault();
                submitMpesaPayment();
            });
        }

        const stripeForm = byId("stripeForm");
        if (stripeForm) {
            stripeForm.addEventListener("submit", (event) => {
                event.preventDefault();
                submitStripePayment();
            });
        }
    }

    function initHomePage() {
        if (!hasHomePage()) {
            return;
        }

        updateNav();
        loadFlights();
        loadMyBookings();
    }

    document.addEventListener("DOMContentLoaded", function () {
        initTheme();
        initScrollProgress();
        initModalDismiss();
        initActionHandlers();
        initHomePage();
    });
}());
