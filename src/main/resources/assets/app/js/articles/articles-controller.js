'use strict';

angular.module('service-testing-tool').controller('ArticlesController', ['$scope', 'Articles', '$stateParams', '$location', '$state',
  function($scope, Articles, $stateParams, $location, $state) {
    $scope.create = function(isValid) {
      if (isValid) {
        var article = new Articles({
          title: this.title,
          content: this.content
        });
        article.$save(function(response) {
          $location.path('articles/' + response.id);
        });

        this.title = '';
        this.content = '';
      } else {
        $scope.submitted = true;
      }
    };

    $scope.update = function(isValid) {
      if (isValid) {
        var article = $scope.article;
        article.$update(function() {
          $location.path('articles/' + article.id);
        });
      } else {
        $scope.submitted = true;
      }
    };

    $scope.remove = function(article) {
      article.$remove(function(response) {
          $state.go($state.current, {}, {reload: true});
      });
    };

    $scope.find = function() {
      Articles.query(function(articles) {
        $scope.articles = articles;
      });
    };

    $scope.findGrid = function() {
      Articles.query(function(articles) {
        $scope.articles = articles;
        $scope.columnDefs = [
          {name: 'title', cellTemplate:'<div><a href="#/articles/{{row.entity.id}}">{{COL_FIELD}}</a></div>'},
          {name: 'content'}
        ];
      });
    };

    $scope.findOne = function() {
      Articles.get({
        articleId: $stateParams.articleId
      }, function(article) {
        $scope.article = article;
      });
    };
  }
]);
