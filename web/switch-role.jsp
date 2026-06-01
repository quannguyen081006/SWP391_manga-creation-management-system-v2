<% String qs = request.getQueryString(); response.sendRedirect("main/switch-role" + (qs == null || qs.isEmpty() ? "" : "?" + qs)); %>
