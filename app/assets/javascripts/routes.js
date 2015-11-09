/**
 * mapping to global *Routes* object from play generated jsRoutes
 *
 * @type {{classify: function}}
 */
export var Routes = {
    classify: (keyword) => jsRoutes.controllers.Application.classify(keyword).url
};