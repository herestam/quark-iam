package com.example.emailapp.common.web;

import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Renders a Qute page together with the data every template depends on.
 *
 * <p>Wiring the shared attributes in one place means a new page cannot forget
 * the sidebar section or the flash message, and strict rendering stays happy
 * because every attribute is always present.</p>
 */
@ApplicationScoped
public class PageRenderer {

    @Inject
    Views views;

    @Inject
    Ui ui;

    public String render(String view, UiSupport.Section section, Params params) {
        return render(view, section, params, Map.of());
    }

    public String render(String view, UiSupport.Section section, Params params, Map<String, Object> data) {
        Params query = params == null ? new Params() : params;
        var instance = views.render(view)
                .data("section", section.name())
                .data("ui", ui)
                .data("params", query)
                .data("flash", Flash.of(query.get("flash", null), query.get("msg", null)));
        data.forEach(instance::data);
        return instance.render();
    }
}
